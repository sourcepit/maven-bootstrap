/*
 * Copyright (C) 2011 Bosch Software Innovations GmbH. All rights reserved.
 */

package org.sourcepit.maven.bootstrap.core;

import java.io.File;
import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ResolutionErrorHandler;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.DuplicateProjectException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.maven.project.ProjectSorter;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorldListener;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.dag.CycleDetectedException;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.repository.LocalRepositoryManager;
import org.sonatype.guice.bean.locators.BeanLocator;
import org.sonatype.inject.BeanEntry;
import org.sourcepit.guplex.Guplex;
import org.sourcepit.guplex.InjectorRequest;
import org.sourcepit.maven.bootstrap.participation.BootstrapParticipant;
import org.sourcepit.maven.bootstrap.participation.BootstrapParticipant2;
import org.sourcepit.maven.bootstrap.participation.BootstrapSession;
import org.sourcepit.maven.exec.intercept.MavenExecutionParticipant;

import com.google.inject.Injector;
import com.google.inject.Key;

public abstract class AbstractBootstrapper2 implements MavenExecutionParticipant
{
   @Requirement
   private Logger logger;

   @Requirement
   private PlexusContainer plexusContainer;

   @Requirement
   private Guplex guplex;

   @Requirement
   private LegacySupport legacySupport;

   @Requirement
   private ProjectBuilder projectBuilder;

   private final ImportEnforcer importEnforcer;

   private final Set<String> extensionRealmPrefixes = new HashSet<String>();

   private final Map<MavenSession, MavenSession> originalToBootSession = new HashMap<MavenSession, MavenSession>();

   @Requirement
   private ProjectDependenciesResolver dependencyResolver;
   
   @Requirement
   private RepositorySystem repositorySystem;

   public AbstractBootstrapper2(String groupId, String artifactId)
   {
      final List<String> imports = new ArrayList<String>();
      imports.add("javax.inject.*");
      imports.add("com.google.inject.*");
      imports.add("com.google.inject.name.*");
      imports.add("org.sonatype.inject.*");

      imports.add(ImportEnforcer.toImportPattern(BootstrapSession.class));
      imports.add(ImportEnforcer.toImportPattern(BootstrapParticipant.class));

      final StringBuilder realmPrefix = new StringBuilder();
      realmPrefix.append("extension>");
      realmPrefix.append(groupId);
      realmPrefix.append(":");
      realmPrefix.append(artifactId);

      extensionRealmPrefixes.add(realmPrefix.toString());

      importEnforcer = new ImportEnforcer(getClass().getClassLoader(), extensionRealmPrefixes, imports);
   }

   public void executionStarted(MavenSession session, MavenExecutionRequest executionRequest)
      throws MavenExecutionException
   {
      plexusContainer.getContainerRealm().getWorld().addListener(importEnforcer);

      final MavenSession bootSession = session.clone();
      originalToBootSession.put(session, bootSession);

      final MavenSession oldSession = legacySupport.getSession();
      try
      {
         legacySupport.setSession(bootSession);

         setupBootSession(bootSession);
         performBootSession(bootSession);
         adjustActualSession(bootSession, session);
      }
      finally
      {
         legacySupport.setSession(oldSession);
      }
   }

   private void setupBootSession(MavenSession bootSession)
   {
      final Collection<File> descriptors = new LinkedHashSet<File>();
      final Collection<File> skippedDescriptors = new HashSet<File>();
      discoverProjectDescriptors(bootSession, descriptors, skippedDescriptors);

      bootSession.setProjects(buildBootstrapProjects(bootSession, descriptors, skippedDescriptors));
   }

   private void performBootSession(final MavenSession bootSession)
   {
      for (MavenProject bootProject : bootSession.getProjects())
      {
         bootSession.setCurrentProject(bootProject);

         final List<ClassRealm> bootExtensionClassRealms = discoverBootExtensionClassRealms(bootProject);
         for (ClassRealm bootExtensionClassRealm : bootExtensionClassRealms)
         {
            performBootProject(bootSession, bootProject, bootExtensionClassRealm);
         }
      }
   }

   private void performBootProject(MavenSession bootSession, MavenProject bootProject,
      ClassRealm bootExtensionClassRealm)
   {
      // TODO remove
      resolveDependencies(bootProject, bootSession.getRepositorySession());

      final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(bootExtensionClassRealm);
      try
      {
         final List<BootstrapParticipant2> bootParticipants = discoverBootstrapParticipants(bootSession, bootProject,
            bootExtensionClassRealm);
         for (BootstrapParticipant2 bootParticipant : bootParticipants)
         {
            bootParticipant.beforeBuild(bootSession, bootProject);
         }
      }
      finally
      {
         Thread.currentThread().setContextClassLoader(originalClassLoader);
      }
   }

   private DependencyResolutionResult resolveDependencies(MavenProject project, RepositorySystemSession session)
   {
      DependencyResolutionResult resolutionResult = null;

      try
      {
         DefaultDependencyResolutionRequest resolution = new DefaultDependencyResolutionRequest(project, session);
         resolutionResult = dependencyResolver.resolve(resolution);
      }
      catch (DependencyResolutionException e)
      {
         resolutionResult = e.getResult();
      }

      Set<Artifact> artifacts = new LinkedHashSet<Artifact>();
      if (resolutionResult.getDependencyGraph() != null)
      {
         RepositoryUtils.toArtifacts(artifacts, resolutionResult.getDependencyGraph().getChildren(),
            Collections.singletonList(project.getArtifact().getId()), null);

         // Maven 2.x quirk: an artifact always points at the local repo, regardless whether resolved or not
         LocalRepositoryManager lrm = session.getLocalRepositoryManager();
         for (Artifact artifact : artifacts)
         {
            if (!artifact.isResolved())
            {
               String path = lrm.getPathForLocalArtifact(RepositoryUtils.toArtifact(artifact));
               artifact.setFile(new File(lrm.getRepository().getBasedir(), path));
            }
         }
      }
      project.setResolvedArtifacts(artifacts);
      project.setArtifacts(artifacts);

      return resolutionResult;
   }

   // TODO abstract
   private void adjustActualSession(MavenSession bootSession, MavenSession actualSession)
   {
      final File baseDir;
      if (bootSession.getRequest().getPom() == null)
      {
         baseDir = bootSession.getRequest().getPom().getParentFile();
      }
      else
      {
         baseDir = new File(bootSession.getRequest().getBaseDirectory());
      }
   }

   public void executionEnded(MavenSession session, MavenExecutionResult executionResult)
   {
      final MavenSession oldSession = legacySupport.getSession();
      try
      {
         final MavenSession bootSession = originalToBootSession.remove(session);
         legacySupport.setSession(bootSession);
         shutdownBootSession(bootSession);
      }
      finally
      {
         legacySupport.setSession(oldSession);
      }

      plexusContainer.getContainerRealm().getWorld().removeListener(importEnforcer);
   }

   private void shutdownBootSession(MavenSession bootSession)
   {
      for (MavenProject bootProject : bootSession.getProjects())
      {
         bootSession.setCurrentProject(bootProject);

         final List<ClassRealm> bootExtensionClassRealms = discoverBootExtensionClassRealms(bootProject);
         for (ClassRealm bootExtensionClassRealm : bootExtensionClassRealms)
         {
            shutdownBootProject(bootSession, bootProject, bootExtensionClassRealm);
         }
      }
   }

   private void shutdownBootProject(MavenSession bootSession, MavenProject bootProject,
      ClassRealm bootExtensionClassRealm)
   {
      final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(bootExtensionClassRealm);
      try
      {
         final List<BootstrapParticipant2> bootParticipants = discoverBootstrapParticipants(bootSession, bootProject,
            bootExtensionClassRealm);
         for (BootstrapParticipant2 bootParticipant : bootParticipants)
         {
            bootParticipant.afterBuild(bootSession, bootProject);
         }
      }
      finally
      {
         Thread.currentThread().setContextClassLoader(originalClassLoader);
      }
   }

   private List<BootstrapParticipant2> discoverBootstrapParticipants(MavenSession bootSession,
      MavenProject bootProject, ClassRealm bootExtensionClassRealm)
   {
      final String key = BootstrapParticipant2.class.getName() + "@" + bootExtensionClassRealm.getId();

      @SuppressWarnings("unchecked")
      List<BootstrapParticipant2> bootstrapParticipants = (List<BootstrapParticipant2>) bootProject
         .getContextValue(key);
      if (bootstrapParticipants == null)
      {
         bootstrapParticipants = lookupBootstrapParticipants(bootSession, bootProject, bootExtensionClassRealm);
         bootProject.setContextValue(key, bootstrapParticipants);
      }
      return bootstrapParticipants;
   }

   private List<BootstrapParticipant2> lookupBootstrapParticipants(MavenSession bootSession, MavenProject bootProject,
      ClassRealm bootExtensionClassRealm)
   {
      final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(bootExtensionClassRealm);
      try
      {
         InjectorRequest request = new InjectorRequest();
         request.setUseIndex(true);
         request.getClassLoaders().add(bootExtensionClassRealm);
         addCustomClassLoaders(bootSession, bootProject, bootExtensionClassRealm, request.getClassLoaders());
         
         final Injector injector = guplex.createInjector(request);

         final BeanLocator locator = injector.getInstance(BeanLocator.class);
         final Key<BootstrapParticipant2> key = Key.get(BootstrapParticipant2.class);
         final List<BootstrapParticipant2> result = new ArrayList<BootstrapParticipant2>();
         for (BeanEntry<Annotation, BootstrapParticipant2> beanEntry : locator.locate(key))
         {
            result.add(beanEntry.getValue());
         }
         return result;
      }
      finally
      {
         Thread.currentThread().setContextClassLoader(originalClassLoader);
      }
   }
   
   @Requirement
   private ResolutionErrorHandler resolutionErrorHandler;

   private void addCustomClassLoaders(MavenSession session, MavenProject project, ClassRealm extensionRealm,
      Set<ClassLoader> classLoaders)
   {
      final Dependency dependency = new Dependency();
      dependency.setGroupId("org.sourcepit.b2");
      dependency.setArtifactId("b2-p2-site-generator");
      dependency.setVersion("0.1.0-SNAPSHOT");

      final ArtifactResolutionResult result = resolve(session, project, dependency);

      final Set<org.apache.maven.artifact.Artifact> artifacts = result.getArtifacts();
      final URL[] urls = new URL[artifacts.size()];
      int i = 0;
      for (Iterator<org.apache.maven.artifact.Artifact> it = artifacts.iterator(); it.hasNext(); i++)
      {
         final org.apache.maven.artifact.Artifact artifact = it.next();
         try
         {
            urls[i] = artifact.getFile().toURL();
         }
         catch (MalformedURLException e)
         {
            throw new IllegalStateException(e);
         }
      }

      classLoaders.add(new URLClassLoader(urls, extensionRealm));
   }

   private ArtifactResolutionResult resolve(MavenSession session, MavenProject project, Dependency dependency)
   {
      final ArtifactResolutionRequest request = new ArtifactResolutionRequest();
      request.setResolveRoot(true);
      request.setResolveTransitively(true);
      request
         .setResolutionFilter(new ScopeArtifactFilter(org.apache.maven.artifact.Artifact.SCOPE_RUNTIME_PLUS_SYSTEM));
      request
         .setCollectionFilter(new ScopeArtifactFilter(org.apache.maven.artifact.Artifact.SCOPE_RUNTIME_PLUS_SYSTEM));

      final MavenExecutionRequest executionRequest = session.getRequest();
      request.setForceUpdate(executionRequest.isUpdateSnapshots());
      request.setServers(executionRequest.getServers());
      request.setMirrors(executionRequest.getMirrors());
      request.setProxies(executionRequest.getProxies());

      request.setOffline(session.isOffline());
      request.setLocalRepository(session.getLocalRepository());

      // project specific
      request.setRemoteRepositories(project.getRemoteArtifactRepositories());
      request.setManagedVersionMap(project.getManagedVersionMap());

      request.setArtifact(repositorySystem.createDependencyArtifact(dependency));

      final ArtifactResolutionResult result = repositorySystem.resolve(request);
      try
      {
         resolutionErrorHandler.throwErrors(request, result);
      }
      catch (ArtifactResolutionException e)
      {
         throw new IllegalStateException(e);
      }
      return result;
   }

   private List<ClassRealm> discoverBootExtensionClassRealms(MavenProject bootProject)
   {
      final List<ClassRealm> bootExtensionRelams = new ArrayList<ClassRealm>();

      final ClassRealm projectRealm = bootProject.getClassRealm();
      if (projectRealm != null)
      {
         @SuppressWarnings("unchecked")
         final Collection<ClassRealm> importRealms = projectRealm.getImportRealms();
         for (ClassRealm classRealm : importRealms)
         {
            if (isBootExtensionClassRealm(extensionRealmPrefixes, classRealm))
            {
               bootExtensionRelams.add(classRealm);
            }
         }
      }

      return bootExtensionRelams;
   }

   private static boolean isBootExtensionClassRealm(Collection<String> extensionRealmPrefixes, ClassRealm realm)
   {
      for (String realmPrefix : extensionRealmPrefixes)
      {
         if (realm.getId().startsWith(realmPrefix))
         {
            return true;
         }
      }
      return false;
   }

   // TODO extract
   private List<MavenProject> buildBootstrapProjects(MavenSession session, Collection<File> descriptors,
      Collection<File> skippedDescriptors)
   {
      final ProjectBuildingRequest request = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
      request.setRemoteRepositories(filterArtifactRepositories(request.getRemoteRepositories()));

      final List<File> pomFiles = new ArrayList<File>();
      for (File descriptor : descriptors)
      {
         if (skippedDescriptors.contains(descriptor))
         {
            logger.info("Skipping module descriptor " + descriptor.getPath());
            continue;
         }
         pomFiles.add(descriptor);
      }

      final List<ProjectBuildingResult> results;
      try
      {
         results = projectBuilder.build(pomFiles, false, request);
      }
      catch (ProjectBuildingException e)
      {
         throw new IllegalStateException("Cannot build bootstrapper project for " + e.getPomFile(), e);
      }

      final List<MavenProject> projects = new ArrayList<MavenProject>(results.size());
      for (ProjectBuildingResult result : results)
      {
         final MavenProject project = result.getProject();
         project.setRemoteArtifactRepositories(filterArtifactRepositories(project.getRemoteArtifactRepositories()));
         project.setPluginArtifactRepositories(filterArtifactRepositories(project.getPluginArtifactRepositories()));
         projects.add(project);
      }

      final ProjectSorter projectSorter;
      try
      {
         projectSorter = new ProjectSorter(projects);
      }
      catch (CycleDetectedException e)
      {
         throw new IllegalStateException(e);
      }
      catch (DuplicateProjectException e)
      {
         throw new IllegalStateException(e);
      }

      return projectSorter.getSortedProjects();
   }

   // TODO abstract / configurable
   private List<ArtifactRepository> filterArtifactRepositories(List<ArtifactRepository> remoteRepositories)
   {
      final List<ArtifactRepository> result = new ArrayList<ArtifactRepository>();
      for (ArtifactRepository remoteRepository : remoteRepositories)
      {
         if (!"p2".equals(remoteRepository.getLayout().getId()))
         {
            result.add(remoteRepository);
         }
      }
      return result;
   }

   // TODO abstract
   private void discoverProjectDescriptors(MavenSession session, Collection<File> descriptors,
      Collection<File> skippedDescriptors)
   {

   }

   private final static class ImportEnforcer implements ClassWorldListener
   {
      private final ClassLoader classLoader;

      private final Set<String> extensionRealmPrefixes;

      private final List<String> imports = new ArrayList<String>();

      public ImportEnforcer(ClassLoader classLoader, Set<String> extensionRealmPrefixes, List<String> imports)
      {
         this.classLoader = classLoader;
         this.extensionRealmPrefixes = extensionRealmPrefixes;
         this.imports.addAll(imports);
      }

      private static String toImportPattern(Class<?> clazz)
      {
         final String name = clazz.getName();
         final StringBuilder sb = new StringBuilder();
         sb.append(name.substring(0, name.lastIndexOf('.')));
         sb.append(".*");
         return sb.toString();
      }

      public void realmCreated(ClassRealm realm)
      {
         if (isBootExtensionClassRealm(extensionRealmPrefixes, realm))
         {
            for (String packageImport : imports)
            {
               realm.importFrom(classLoader, packageImport);
            }
         }
      }

      public void realmDisposed(ClassRealm realm)
      {
      }
   }
}
