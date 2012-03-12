/*
 * Copyright (C) 2011 Bosch Software Innovations GmbH. All rights reserved.
 */

package org.sourcepit.maven.exec.bootstrap;

import org.apache.maven.project.MavenProject;

public interface BuildBootstrapParticipant
{
   void beforeBuild(BootstrapSession bootstrapSession, MavenProject bootstrapProject);

   void afterBuild(BootstrapSession bootstrapSession, MavenProject bootstrapProject);
}
