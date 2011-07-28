/*
 * Copyright (C) 2011 Bosch Software Innovations GmbH. All rights reserved.
 */

package org.sourcepit.beef.maven.wrapper.internal.session;

import java.util.Collections;
import java.util.List;

import org.apache.maven.project.MavenProject;

public class BootstrapSession
{
   private final List<MavenProject> wrapperProjects;

   public BootstrapSession(List<MavenProject> wrapperProjects)
   {
      this.wrapperProjects = wrapperProjects;
   }

   public List<MavenProject> getWrapperProjects()
   {
      return Collections.unmodifiableList(wrapperProjects);
   }
}
