/*
 * Copyright (C) 2011 Bosch Software Innovations GmbH. All rights reserved.
 */

package org.sourcepit.maven.bootstrap.core;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Repository;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.DuplicateProjectException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.project.ProjectRealmCache;
import org.apache.maven.project.ProjectSorter;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.ClassWorldListener;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.NoSuchRealmException;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.dag.CycleDetectedException;
import org.sonatype.aether.AbstractRepositoryListener;
import org.sonatype.aether.RepositoryEvent;
import org.sonatype.aether.RepositoryListener;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.repository.WorkspaceReader;
import org.sonatype.aether.util.DefaultRepositorySystemSession;
import org.sonatype.aether.util.listener.ChainedRepositoryListener;
import org.sonatype.guice.bean.locators.BeanLocator;
import org.sonatype.inject.BeanEntry;
import org.sourcepit.guplex.Guplex;
import org.sourcepit.guplex.InjectorRequest;
import org.sourcepit.maven.bootstrap.internal.core.ReactorReader;
import org.sourcepit.maven.bootstrap.participation.BootstrapSession;
import org.sourcepit.maven.bootstrap.participation.BootstrapParticipant;
import org.sourcepit.maven.exec.intercept.MavenExecutionParticipant;

import com.google.inject.Injector;
import com.google.inject.Key;

public abstract class AbstractBootstrapper implements MavenExecutionParticipant
{
   private final Map<Thread, List<MavenProject>> threadToProjects = new LinkedHashMap<Thread, List<MavenProject>>();

   @Requirement
   private Logger logger;

   @Requirement
   private PlexusContainer plexusContainer;

   @Requirement
   private ProjectBuilder projectBuilder;

   @Requirement
   private ProjectRealmCache projectRealmCache;

   private final ImportEnforcer importEnforcer;

   private final Set<String> extensionRealmPrefixes = new HashSet<String>();

   public AbstractBootstrapper(String groupId, String artifactId)
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

   private BootstrapSession bootstrapSession;

   public void executionStarted(MavenSession session, MavenExecutionRequest executionRequest)
      throws MavenExecutionException
   {
      plexusContainer.getContainerRealm().getWorld().addListener(importEnforcer);

      final Collection<File> descriptors = new LinkedHashSet<File>();
      final Collection<File> skippedDescriptors = new HashSet<File>();
      getModuleDescriptors(session, descriptors, skippedDescriptors);

      final List<MavenProject> bootstrapProjects = createBootstrapProjects(session, descriptors, skippedDescriptors);
      bootstrapSession = new BootstrapSession(bootstrapProjects, skippedDescriptors);

      beforeBootstrapProjects(session, bootstrapProjects);
      try
      {
         fireBeforeBuild(bootstrapSession, true);
      }
      finally
      {
         disposeProjectRealms(bootstrapProjects);
      }
      afterWrapperProjectsInitialized(session, bootstrapProjects);
   }

   protected abstract void beforeBootstrapProjects(MavenSession session, List<MavenProject> projects)
      throws MavenExecutionException;

   protected abstract void afterWrapperProjectsInitialized(MavenSession session, List<MavenProject> projects)
      throws MavenExecutionException;

   protected abstract void getModuleDescriptors(MavenSession session, Collection<File> descriptors,
      Collection<File> skippedDescritors) throws MavenExecutionException;

   public void executionEnded(MavenSession session, MavenExecutionResult executionResult)
   {
      final List<MavenProject> wrapperProject = threadToProjects.remove(Thread.currentThread());
      if (wrapperProject != null)
      {
         executionEnded(wrapperProject);
      }
      plexusContainer.getContainerRealm().getWorld().removeListener(importEnforcer);
   }

   private void executionEnded(List<MavenProject> projects)
   {
      for (MavenProject project : projects)
      {
         fireBuildEvent(BuildEvent.AFTER, bootstrapSession, project);
      }
   }

   private void disposeProjectRealms(List<MavenProject> projects)
   {
      final ClassWorld classWorld = plexusContainer.getContainerRealm().getWorld();
      for (MavenProject project : projects)
      {
         final ClassRealm classRealm = project.getClassRealm();
         if (classRealm != null)
         {
            try
            {
               classWorld.disposeRealm(classRealm.getId());
            }
            catch (NoSuchRealmException e)
            { // noop
            }
         }
      }
      projectRealmCache.flush();
   }

   private void fireBeforeBuild(BootstrapSession bootstrapSession, boolean resolveDependencies)
      throws MavenExecutionException
   {
      WorkspaceReader reactorReader = null;

      for (MavenProject bootstrapProject : bootstrapSession.getBootstrapProjects())
      {
         bootstrapSession.setCurrentProject(bootstrapProject);

         if (resolveDependencies)
         {
            if (reactorReader == null)
            {
               reactorReader = new ReactorReader(ReactorReader.getProjectMap(bootstrapSession.getBootstrapProjects()));
            }
            resolveDependencies(bootstrapSession, bootstrapProject, reactorReader);
         }

         fireBuildEvent(BuildEvent.BEFORE, bootstrapSession, bootstrapProject);
      }
   }

   private void fireBuildEvent(BuildEvent event, final BootstrapSession session, final MavenProject project)
   {
      for (BootstrapParticipant bootstrapParticipants : getBootstrapParticipants(project))
      {
         logger.info("Invoking bootstrap participant '" + bootstrapParticipants.getClass().getSimpleName()
            + "' on project '" + project.getName() + "'");
         try
         {
            switch (event)
            {
               case BEFORE :
                  bootstrapParticipants.beforeBuild(session, project);
                  break;
               case AFTER :
                  bootstrapParticipants.afterBuild(session, project);
                  break;
               default :
                  throw new IllegalStateException();
            }
         }
         catch (RuntimeException e)
         {
            logger.error("Invoking buildwrapper '" + bootstrapParticipants.getClass().getSimpleName()
               + "' on project '" + project.getName() + "' failed", e);
         }
         catch (Exception e)
         {
            logger.error("Invoking buildwrapper '" + bootstrapParticipants.getClass().getSimpleName()
               + "' on project '" + project.getName() + "' failed", e);
         }
      }
   }

   private List<BootstrapParticipant> getBootstrapParticipants(MavenProject project)
   {
      final String fqn = BootstrapParticipant.class.getName();

      @SuppressWarnings("unchecked")
      List<BootstrapParticipant> bootstrapParticipants = (List<BootstrapParticipant>) project.getContextValue(fqn);
      if (bootstrapParticipants != null)
      {
         return bootstrapParticipants;
      }

      final ClassRealm projectRealm = project.getClassRealm();
      if (projectRealm == null) // no extensions for this project
      {
         bootstrapParticipants = new ArrayList<BootstrapParticipant>();
      }
      else
      {
         bootstrapParticipants = lookupBootstrapParticipants(projectRealm);
      }

      project.setContextValue(fqn, bootstrapParticipants);

      return bootstrapParticipants;
   }

   private List<BootstrapParticipant> lookupBootstrapParticipants(final ClassRealm projectRealm)
   {
      final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(projectRealm);
      try
      {
         InjectorRequest request = new InjectorRequest();
         request.setUseIndex(true);

         collectRealms(request.getClassLoaders(), projectRealm);

         final Guplex guplex = plexusContainer.lookup(Guplex.class);

         final Injector injector = guplex.createInjector(request);
         final BeanLocator locator = injector.getInstance(BeanLocator.class);

         final Key<BootstrapParticipant> key = Key.get(BootstrapParticipant.class);
         final List<BootstrapParticipant> result = new ArrayList<BootstrapParticipant>();
         for (BeanEntry<Annotation, BootstrapParticipant> beanEntry : locator.locate(key))
         {
            result.add(beanEntry.getValue());
         }
         return result;
      }
      catch (ComponentLookupException e)
      {
         throw new IllegalStateException(e);
      }
      finally
      {
         Thread.currentThread().setContextClassLoader(originalClassLoader);
      }
   }

   private void collectRealms(Collection<ClassLoader> realms, ClassRealm classRealm)
   {
      if (!realms.contains(classRealm))
      {
         if (isExtensionRealm(extensionRealmPrefixes, classRealm))
         {
            realms.add(classRealm);
         }

         @SuppressWarnings("unchecked")
         Collection<ClassRealm> importRealms = classRealm.getImportRealms();
         for (ClassRealm importRealm : importRealms)
         {
            collectRealms(realms, importRealm);
         }
      }
   }

   private static boolean isExtensionRealm(Collection<String> extensionRealmPrefixes, ClassRealm realm)
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

   private List<MavenProject> createBootstrapProjects(MavenSession session, Collection<File> descriptors,
      Collection<File> skippedDescriptors) throws MavenExecutionException
   {
      final MavenExecutionRequest execRequest = session.getRequest();

      final DefaultProjectBuildingRequest projectBuildingRequest = new DefaultProjectBuildingRequest();
      projectBuildingRequest.setLocalRepository(execRequest.getLocalRepository());
      projectBuildingRequest.setSystemProperties(execRequest.getSystemProperties());
      projectBuildingRequest.setUserProperties(execRequest.getUserProperties());

      final List<ArtifactRepository> remoteRepositories = new ArrayList<ArtifactRepository>();
      for (ArtifactRepository remoteRepository : execRequest.getRemoteRepositories())
      {
         if (!"p2".equals(remoteRepository.getLayout().getId()))
         {
            remoteRepositories.add(remoteRepository);
         }
      }
      projectBuildingRequest.setRemoteRepositories(remoteRepositories);

      projectBuildingRequest.setPluginArtifactRepositories(execRequest.getPluginArtifactRepositories());
      projectBuildingRequest.setActiveProfileIds(execRequest.getActiveProfiles());
      projectBuildingRequest.setInactiveProfileIds(execRequest.getInactiveProfiles());
      projectBuildingRequest.setProfiles(execRequest.getProfiles());
      projectBuildingRequest.setProcessPlugins(true);
      projectBuildingRequest.setBuildStartTime(execRequest.getStartTime());
      projectBuildingRequest.setRepositorySession(session.getRepositorySession());
      projectBuildingRequest.setResolveDependencies(true);

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
         results = projectBuilder.build(pomFiles, false, projectBuildingRequest);
      }
      catch (ProjectBuildingException e)
      {
         throw new MavenExecutionException("Cannot build bootstrapper project for " + e.getPomFile(), e);
      }

      final List<MavenProject> projects = new ArrayList<MavenProject>(results.size());
      for (ProjectBuildingResult result : results)
      {
         MavenProject project = result.getProject();

         final List<ArtifactRepository> remoteArtifactRepositories = new ArrayList<ArtifactRepository>();
         for (ArtifactRepository remoteRepository : project.getRemoteArtifactRepositories())
         {
            if (!"p2".equals(remoteRepository.getLayout().getId()))
            {
               remoteArtifactRepositories.add(remoteRepository);
            }
         }
         project.setRemoteArtifactRepositories(remoteArtifactRepositories);

         final List<ArtifactRepository> remotePluginRepositories = new ArrayList<ArtifactRepository>();
         for (ArtifactRepository remoteRepository : project.getPluginArtifactRepositories())
         {
            if (!"p2".equals(remoteRepository.getLayout().getId()))
            {
               remotePluginRepositories.add(remoteRepository);
            }
         }
         project.setPluginArtifactRepositories(remotePluginRepositories);

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

      final List<MavenProject> sortedProjects = projectSorter.getSortedProjects();
      if (threadToProjects.containsKey(Thread.currentThread()))
      {
         throw new IllegalStateException();
      }
      threadToProjects.put(Thread.currentThread(), sortedProjects);

      return sortedProjects;
   }

   private void resolveDependencies(final BootstrapSession bootSession, MavenProject mavenProject,
      WorkspaceReader reactorReader)
   {
      final ProjectBuildingRequest request = mavenProject.getProjectBuildingRequest();
      DefaultRepositorySystemSession repoSession = (DefaultRepositorySystemSession) request.getRepositorySession();

      RepositoryListener repositoryListener = repoSession.getRepositoryListener();

      ChainedRepositoryListener chainedRepositoryListener = new ChainedRepositoryListener();
      chainedRepositoryListener.add(repositoryListener);
      chainedRepositoryListener.add(new AbstractRepositoryListener()
      {
         @Override
         public void artifactDownloaded(RepositoryEvent event)
         {
            final Artifact artifact = event.getArtifact();
            final File file = artifact.getFile();

            if (event.getException() == null && file != null && artifact.getFile().exists())
            {
               if (event.getRepository() instanceof RemoteRepository)
               {
                  final RemoteRepository remoteRepository = (RemoteRepository) event.getRepository();

                  final String repoId = remoteRepository.getId();
                  final String repoUrl = remoteRepository.getUrl();

                  final Repository r = new Repository();
                  r.setId(repoId);
                  r.setUrl(repoUrl);

                  @SuppressWarnings("unchecked")
                  Map<File, Repository> fileToRepositoryMap = (Map<File, Repository>) bootSession.getData("downloads");
                  if (fileToRepositoryMap == null)
                  {
                     fileToRepositoryMap = new HashMap<File, Repository>();
                     bootSession.setData("downloads", fileToRepositoryMap);
                  }
                  fileToRepositoryMap.put(file, r);
               }
            }
         }
      });

      repoSession.setRepositoryListener(chainedRepositoryListener);

      final List<ArtifactRepository> oldRepos = request.getRemoteRepositories();
      final List<ArtifactRepository> oldPluginRepos = request.getPluginArtifactRepositories();
      try
      {
         repoSession.setWorkspaceReader(reactorReader);
         request.setProject(mavenProject);

         List<ArtifactRepository> remoteRepositories = new ArrayList<ArtifactRepository>();
         for (ArtifactRepository remoteRepository : mavenProject.getRemoteArtifactRepositories())
         {
            if (!"p2".equals(remoteRepository.getLayout().getId()))
            {
               remoteRepositories.add(remoteRepository);
            }
         }
         request.setRemoteRepositories(remoteRepositories);

         remoteRepositories = new ArrayList<ArtifactRepository>();
         for (ArtifactRepository remoteRepository : mavenProject.getPluginArtifactRepositories())
         {
            if (!"p2".equals(remoteRepository.getLayout().getId()))
            {
               remoteRepositories.add(remoteRepository);
            }
         }
         request.setPluginArtifactRepositories(remoteRepositories);

         projectBuilder.build(mavenProject.getFile(), request);
      }
      catch (ProjectBuildingException e)
      {
         throw new IllegalStateException(e);
      }
      finally
      {
         request.setProject(null);
         request.setRemoteRepositories(oldRepos);
         request.setPluginArtifactRepositories(oldPluginRepos);
         repoSession.setWorkspaceReader(null);
         repoSession.setRepositoryListener(repositoryListener);
      }
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
         if (isExtensionRealm(extensionRealmPrefixes, realm))
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

   private enum BuildEvent
   {
      BEFORE, AFTER
   }
}
