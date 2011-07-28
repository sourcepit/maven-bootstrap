/*
 * Copyright (C) 2011 Bosch Software Innovations GmbH. All rights reserved.
 */

package org.sourcepit.beef.maven.wrapper.internal.session;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.project.ProjectRealmCache;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
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

   private AtomicBoolean isDisposed = new AtomicBoolean(false);

   public void sessionAboutToStart(MavenSession session) throws MavenExecutionException
   {
      final List<ModelSource> modelSources = createWrapperProjectsModelSources(session);
      final List<MavenProject> wrapperProjects = createWrapperProjects(session, modelSources);
      try
      {
         invoke("beforeProjectBuild", wrapperProjects);
      }
      finally
      {
         disposeProjectRealms(wrapperProjects);
      }
      afterWrapperProjectsInitialized(session, wrapperProjects);
   }

   protected abstract void afterWrapperProjectsInitialized(MavenSession session, List<MavenProject> projects)
      throws MavenExecutionException;

   protected abstract List<ModelSource> createWrapperProjectsModelSources(MavenSession session)
      throws MavenExecutionException;

   public void sessionEnded()
   {
      final List<MavenProject> wrapperProject = threadToProjects.remove(Thread.currentThread());
      if (wrapperProject != null)
      {
         sessionEnded(wrapperProject);
      }
   }

   public void dispose()
   {
      if (isDisposed.compareAndSet(false, true))
      {
         final Set<Entry<Thread, List<MavenProject>>> entrySet = threadToProjects.entrySet();
         threadToProjects.clear();
         for (Entry<Thread, List<MavenProject>> entry : entrySet)
         {
            sessionEnded(entry.getValue());
         }
      }
   }

   private void sessionEnded(List<MavenProject> projects)
   {
      try
      {
         invoke("afterProjectBuild", projects);
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

   private void invoke(String methodName, final List<MavenProject> projects) throws MavenExecutionException
   {
      for (MavenProject project : projects)
      {
         invoke(methodName, project);
      }
   }

   private void invoke(String methodName, final MavenProject project) throws MavenExecutionException
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
                  final Method method = buildwrapper.getClass().getMethod(methodName, project.getClass());

                  logger.info("Invoking buildwrapper '" + buildwrapper.getClass().getSimpleName() + "' on project '"
                     + project.getName() + "'");

                  method.invoke(buildwrapper, project);
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
      final String fqn = "org.sourcepit.beef.maven.wrapper.internal.session.IBootstrapedProjectBuildListener";

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

   private List<MavenProject> createWrapperProjects(MavenSession session, List<ModelSource> modelSources)
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

      final List<MavenProject> projects = new ArrayList<MavenProject>(modelSources.size());
      for (ModelSource modelSource : modelSources)
      {
         try
         {
            final ProjectBuildingResult result = projectBuilder.build(modelSource, projectBuildingRequest);
            final MavenProject project = result.getProject();
            // TODO bernd assert location not null
            project.setFile(new File(modelSource.getLocation()));
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
}
