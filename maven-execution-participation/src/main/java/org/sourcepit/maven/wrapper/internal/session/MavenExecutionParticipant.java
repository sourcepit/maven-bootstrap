/*
 * Copyright (C) 2011 Bosch Software Innovations GmbH. All rights reserved.
 */

package org.sourcepit.maven.wrapper.internal.session;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;

public interface MavenExecutionParticipant
{
   void executionStarted(MavenSession session, MavenExecutionRequest executionRequest)
      throws MavenExecutionException;

   void executionEnded(MavenSession session, MavenExecutionResult executionResult);
}
