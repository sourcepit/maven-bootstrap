/*
 * Copyright 2014 Bernd Vogt and others.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
