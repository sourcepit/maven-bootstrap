/*
 * Copyright (C) 2011 Bosch Software Innovations GmbH. All rights reserved.
 */

package org.sourcepit.maven.exec.internal.intercept;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.sourcepit.maven.exec.intercept.MavenExecutionParticipant;

@Named
@Singleton
public class MavenExecutionInterceptor
{
   private final ThreadLocal<MavenSession> sessionThreadLocal = new ThreadLocal<MavenSession>();

   @Inject
   private List<MavenExecutionParticipant> executionParticipants;

   public MavenExecutionInterceptor()
   {
      super();
   }

   public void onAfterSessionStart(MavenSession session) throws MavenExecutionException
   {
      sessionThreadLocal.set(session);
      final MavenExecutionRequest executionRequest = session.getRequest();
      fireExecutionStarted(session, executionRequest);
   }

   public void onSessionEnded(MavenSession mavenSession)
   {
      final MavenSession session = getAndForgetCurrentSession();
      if (session != null)
      {
         fireExecutionEnded(session, session.getResult());
      }
   }

   public void onMavenExecutionResult(MavenExecutionResult executionResult)
   {
      final MavenSession session = getAndForgetCurrentSession();
      if (session != null)
      {
         fireExecutionEnded(session, executionResult);
      }
   }

   private MavenSession getAndForgetCurrentSession()
   {
      // get
      final MavenSession session = sessionThreadLocal.get();
      if (session != null)
      {
         // forget
         sessionThreadLocal.remove();
      }
      return session;
   }

   private void fireExecutionStarted(MavenSession session, final MavenExecutionRequest executionRequest)
      throws MavenExecutionException
   {
      for (MavenExecutionParticipant listener : executionParticipants)
      {
         listener.executionStarted(session, executionRequest);
      }
   }

   private void fireExecutionEnded(final MavenSession session, MavenExecutionResult executionResult)
   {
      for (MavenExecutionParticipant listener : executionParticipants)
      {
         listener.executionEnded(session, executionResult);
      }
   }
}
