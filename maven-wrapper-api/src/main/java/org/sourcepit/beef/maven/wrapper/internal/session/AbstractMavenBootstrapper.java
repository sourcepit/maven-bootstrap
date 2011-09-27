/*
 * Copyright (C) 2011 Bosch Software Innovations GmbH. All rights reserved.
 */

package org.sourcepit.beef.maven.wrapper.internal.session;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
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
import org.sonatype.aether.repository.ArtifactRepository;
import org.sonatype.aether.repository.WorkspaceReader;
import org.sonatype.aether.util.DefaultRepositorySystemSession;
import org.sonatype.aether.util.listener.ChainedRepositoryListener;

public abstract class AbstractMavenBootstrapper implements ISessionListener
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

   private final BootstrapSessionClassLoader bootstrapSessionClassLoader = new BootstrapSessionClassLoader();

   private BootstrapSession bootstrapSession;

   public void sessionAboutToStart(MavenSession session) throws MavenExecutionException
   {
      plexusContainer.getContainerRealm().getWorld().addListener(bootstrapSessionClassLoader);

      final Collection<File> descriptors = new LinkedHashSet<File>();
      final Collection<File> skippedDescriptors = new HashSet<File>();
      getModuleDescriptors(session, descriptors, skippedDescriptors);

      final List<MavenProject> wrapperProjects = createWrapperProjects(session, descriptors, skippedDescriptors);
      bootstrapSession = new BootstrapSession(wrapperProjects, skippedDescriptors);

      beforeWrapperProjectsInitialized(session, wrapperProjects);
      try
      {
         invoke("beforeProjectBuild", bootstrapSession, true);
      }
      finally
      {
         disposeProjectRealms(wrapperProjects);
      }
      afterWrapperProjectsInitialized(session, wrapperProjects);
   }

   protected abstract void beforeWrapperProjectsInitialized(MavenSession session, List<MavenProject> projects)
      throws MavenExecutionException;

   protected abstract void afterWrapperProjectsInitialized(MavenSession session, List<MavenProject> projects)
      throws MavenExecutionException;

   protected abstract void getModuleDescriptors(MavenSession session, Collection<File> descriptors,
      Collection<File> skippedDescritors) throws MavenExecutionException;

   public void sessionEnded()
   {
      final List<MavenProject> wrapperProject = threadToProjects.remove(Thread.currentThread());
      if (wrapperProject != null)
      {
         sessionEnded(wrapperProject);
      }
      plexusContainer.getContainerRealm().getWorld().removeListener(bootstrapSessionClassLoader);
   }

   private void sessionEnded(List<MavenProject> projects)
   {
      try
      {
         invoke("afterProjectBuild", bootstrapSession, false);
      }
      catch (MavenExecutionException e)
      {
         logger.error(e.getLocalizedMessage(), e);
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

   private void invoke(String methodName, BootstrapSession session, boolean resolveDependencies)
      throws MavenExecutionException
   {
      WorkspaceReader reactorReader = null;

      for (MavenProject project : session.getBootstrapProjects())
      {
         session.setCurrentProject(project);

         if (resolveDependencies)
         {
            if (reactorReader == null)
            {
               reactorReader = new ReactorReader(ReactorReader.getProjectMap(session.getBootstrapProjects()));
            }
            resolveDependencies(project, reactorReader);
         }

         invoke(methodName, session, project);
      }
   }

   private void invoke(String methodName, final BootstrapSession session, final MavenProject project)
      throws MavenExecutionException
   {
      final ClassLoader projectRealm = project.getClassRealm();
      if (projectRealm != null)
      {
         final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
         try
         {
            Thread.currentThread().setContextClassLoader(projectRealm);

            for (Object buildwrapper : getBuildwrappers(project))
            {
               try
               {
                  final Method method = buildwrapper.getClass().getMethod(methodName, BootstrapSession.class,
                     MavenProject.class);

                  logger.info("Invoking buildwrapper '" + buildwrapper.getClass().getSimpleName() + "' on project '"
                     + project.getName() + "'");

                  method.invoke(buildwrapper, session, project);
               }
               catch (InvocationTargetException e)
               {
                  throw new MavenExecutionException("Buildwrapping of project '" + project.getName() + "' failed.",
                     e.getCause());
               }
               catch (Exception e)
               {
                  logger.warn("Invoking buildwrapper '" + buildwrapper.getClass().getSimpleName() + "' on project '"
                     + project.getName() + "' failed", e);
               }
            }
         }
         finally
         {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
         }
      }
   }

   private List<Object> getBuildwrappers(MavenProject project)
   {
      final String fqn = IMavenBootstrapperListener.class.getName();

      @SuppressWarnings("unchecked")
      List<Object> wrappers = (List<Object>) project.getContextValue(fqn);
      if (wrappers != null)
      {
         return wrappers;
      }

      try
      {
         wrappers = plexusContainer.lookupList(fqn);
      }
      catch (ComponentLookupException e)
      {
         throw new IllegalStateException(e);
      }

      if (wrappers == null)
      {
         wrappers = new ArrayList<Object>();
      }

      project.setContextValue(fqn, wrappers);

      return wrappers;
   }

   private List<MavenProject> createWrapperProjects(MavenSession session, Collection<File> descriptors,
      Collection<File> skippedDescriptors) throws MavenExecutionException
   {
      final MavenExecutionRequest execRequest = session.getRequest();

      final DefaultProjectBuildingRequest projectBuildingRequest = new DefaultProjectBuildingRequest();
      projectBuildingRequest.setLocalRepository(execRequest.getLocalRepository());
      projectBuildingRequest.setSystemProperties(execRequest.getSystemProperties());
      projectBuildingRequest.setUserProperties(execRequest.getUserProperties());
      projectBuildingRequest.setRemoteRepositories(execRequest.getRemoteRepositories());
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
         projects.add(result.getProject());
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

   private void resolveDependencies(MavenProject mavenProject, WorkspaceReader reactorReader)
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
            if (event.getException() == null)
            {
               Artifact artifact = event.getArtifact();
               
               ArtifactRepository repository = event.getRepository();
            }
         }
      });
      
      repoSession.setRepositoryListener(chainedRepositoryListener);

      try
      {
         repoSession.setWorkspaceReader(reactorReader);
         request.setProject(mavenProject);

         projectBuilder.build(mavenProject.getFile(), request);
      }
      catch (ProjectBuildingException e)
      {
         throw new IllegalStateException(e);
      }
      finally
      {
         request.setProject(null);
         repoSession.setWorkspaceReader(null);
         repoSession.setRepositoryListener(repositoryListener);
      }
   }

   private final static class BootstrapSessionClassLoader extends ClassLoader implements ClassWorldListener
   {
      private final static String PKG_PREFIX = BootstrapSession.class.getName().substring(0,
         BootstrapSession.class.getName().lastIndexOf('.'))
         + ".*";

      @Override
      public Class<?> loadClass(String name) throws ClassNotFoundException
      {
         if (BootstrapSession.class.getName().equals(name))
         {
            return BootstrapSession.class;
         }
         return super.loadClass(name);
      }

      @Override
      public URL getResource(String name)
      {
         return null;
      }

      public void realmCreated(ClassRealm realm)
      {
         realm.importFrom(this, PKG_PREFIX);
      }

      public void realmDisposed(ClassRealm realm)
      {

      }
   }
}
