/*
 * Copyright (C) 2011 Bosch Software Innovations GmbH. All rights reserved.
 */

package org.sourcepit.maven.bootstrap.participation;

import java.util.Map;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

public interface BootstrapParticipant2
{
   void beforeBuild(MavenSession bootSession, MavenProject bootProject, Map<Object, Object> bootContext);

   void afterBuild(MavenSession bootSession, MavenProject bootProject, Map<Object, Object> bootContext);
}
