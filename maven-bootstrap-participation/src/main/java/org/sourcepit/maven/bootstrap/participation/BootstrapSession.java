/*
 * Copyright (C) 2011 Bosch Software Innovations GmbH. All rights reserved.
 */

package org.sourcepit.maven.bootstrap.participation;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

public class BootstrapSession
{
   private final MavenSession mavenSession;
   
   private final List<MavenProject> wrapperProjects;

   private final Collection<File> skippedDescriptors;

   private MavenProject currentProject;

   private final Map<Object, Object> keyToDataMap = new HashMap<Object, Object>();

   public BootstrapSession(MavenSession mavenSession, List<MavenProject> wrapperProjects,
      Collection<File> skippedDescriptors)
   {
      this.mavenSession = mavenSession;
      this.wrapperProjects = wrapperProjects;
      this.skippedDescriptors = skippedDescriptors;
   }
   
   public MavenSession getMavenSession()
   {
      return mavenSession;
   }

   public void setCurrentBootstrapProject(MavenProject currentProject)
   {
      this.currentProject = currentProject;
   }

   public MavenProject getCurrentBootstrapProject()
   {
      return currentProject;
   }

   public List<MavenProject> getBootstrapProjects()
   {
      return Collections.unmodifiableList(wrapperProjects);
   }

   public Collection<File> getSkippedDescriptors()
   {
      return Collections.unmodifiableCollection(skippedDescriptors);
   }

   public Object getData(Object key)
   {
      return keyToDataMap.get(key);
   }

   public void setData(Object key, Object data)
   {
      if (data == null)
      {
         keyToDataMap.remove(key);
      }
      else
      {
         keyToDataMap.put(key, data);
      }
   }
}
