/*
 * Copyright (C) 2011 Bosch Software Innovations GmbH. All rights reserved.
 */

package org.sourcepit.maven.wrapper.internal.session;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;

public interface ISessionListener
{
   void sessionAboutToStart(MavenSession session) throws MavenExecutionException;

   void sessionEnded();
}
