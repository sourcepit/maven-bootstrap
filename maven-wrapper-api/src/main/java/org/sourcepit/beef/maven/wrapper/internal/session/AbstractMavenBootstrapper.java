/*
 * Copyright (C) 2011 Bosch Software Innovations GmbH. All rights reserved.
 */

package org.sourcepit.beef.maven.wrapper.internal.session;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.project.ProjectRealmCache;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.ClassWorldListener;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.NoSuchRealmException;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.logging.Logger;

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

      // plexusContainer.getContainerRealm().getWorld().
      final List<File> descriptors = getModuleDescriptors(session);
      final List<MavenProject> wrapperProjects = createWrapperProjects(session, descriptors);
      bootstrapSession = new BootstrapSession(wrapperProjects);
      try
      {
         invoke("beforeProjectBuild", bootstrapSession);
      }
      finally
      {
         disposeProjectRealms(wrapperProjects);
      }
      afterWrapperProjectsInitialized(session, wrapperProjects);
   }

   protected abstract void afterWrapperProjectsInitialized(MavenSession session, List<MavenProject> projects)
      throws MavenExecutionException;

   protected abstract List<File> getModuleDescriptors(MavenSession session) throws MavenExecutionException;

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
         invoke("afterProjectBuild", bootstrapSession);
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

   private void invoke(String methodName, BootstrapSession session) throws MavenExecutionException
   {
      for (MavenProject project : session.getWrapperProjects())
      {
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

   private List<MavenProject> createWrapperProjects(MavenSession session, List<File> descriptors)
      throws MavenExecutionException
   {
      MavenExecutionRequest r = session.getRequest();

      DefaultProjectBuildingRequest projectBuildingRequest = new DefaultProjectBuildingRequest();
      projectBuildingRequest = new DefaultProjectBuildingRequest();
      projectBuildingRequest.setLocalRepository(r.getLocalRepository());
      projectBuildingRequest.setSystemProperties(r.getSystemProperties());
      projectBuildingRequest.setUserProperties(r.getUserProperties());
      projectBuildingRequest.setRemoteRepositories(r.getRemoteRepositories());
      projectBuildingRequest.setPluginArtifactRepositories(r.getPluginArtifactRepositories());
      projectBuildingRequest.setActiveProfileIds(r.getActiveProfiles());
      projectBuildingRequest.setInactiveProfileIds(r.getInactiveProfiles());
      projectBuildingRequest.setProfiles(r.getProfiles());
      projectBuildingRequest.setProcessPlugins(true);
      projectBuildingRequest.setBuildStartTime(r.getStartTime());
      projectBuildingRequest.setRepositorySession(session.getRepositorySession());
      projectBuildingRequest.setResolveDependencies(true);

      final List<MavenProject> projects = new ArrayList<MavenProject>(descriptors.size());
      for (File descriptor : descriptors)
      {
         try
         {
            final ProjectBuildingResult result = projectBuilder.build(descriptor, projectBuildingRequest);
            final MavenProject project = result.getProject();
            projects.add(project);
         }
         catch (ProjectBuildingException e)
         {
            throw new MavenExecutionException("Cannot build bootstrapper project for " + e.getPomFile(), e);
         }
      }

      if (threadToProjects.containsKey(Thread.currentThread()))
      {
         throw new IllegalStateException();
      }
      threadToProjects.put(Thread.currentThread(), projects);

      return projects;
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
