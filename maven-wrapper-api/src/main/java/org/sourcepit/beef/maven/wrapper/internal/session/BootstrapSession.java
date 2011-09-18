/*
 * Copyright (C) 2011 Bosch Software Innovations GmbH. All rights reserved.
 */

package org.sourcepit.beef.maven.wrapper.internal.session;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.project.MavenProject;

public class BootstrapSession
{
   private final List<MavenProject> wrapperProjects;
   
   private MavenProject currentProject;

   private final Map<String, Object> keyToDataMap = new HashMap<String, Object>();

   public BootstrapSession(List<MavenProject> wrapperProjects)
   {
      this.wrapperProjects = wrapperProjects;
   }
   
   public void setCurrentProject(MavenProject currentProject)
   {
      this.currentProject = currentProject;
   }
   
   public MavenProject getCurrentProject()
   {
      return currentProject;
   }

   public List<MavenProject> getWrapperProjects()
   {
      return Collections.unmodifiableList(wrapperProjects);
   }

   public Object getData(String key)
   {
      return keyToDataMap.get(key);
   }

   public void setData(String key, Object data)
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
