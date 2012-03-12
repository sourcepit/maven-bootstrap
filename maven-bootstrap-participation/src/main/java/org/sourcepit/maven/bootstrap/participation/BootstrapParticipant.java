/*
 * Copyright (C) 2011 Bosch Software Innovations GmbH. All rights reserved.
 */

package org.sourcepit.maven.bootstrap.participation;

import org.apache.maven.project.MavenProject;

public interface BootstrapParticipant
{
   void beforeBuild(BootstrapSession bootSession, MavenProject bootProject);

   void afterBuild(BootstrapSession bootSession, MavenProject bootProject);
}
