/*
 * Copyright (C) 2011 Bosch Software Innovations GmbH. All rights reserved.
 */

package org.sourcepit.beef.maven.wrapper.internal.session;

import org.apache.maven.project.MavenProject;

public interface IMavenBootstrapperListener
{
   void beforeProjectBuild(BootstrapSession session, MavenProject wrapperProject);

   void afterProjectBuild(BootstrapSession session, MavenProject wrapperProject);
}
