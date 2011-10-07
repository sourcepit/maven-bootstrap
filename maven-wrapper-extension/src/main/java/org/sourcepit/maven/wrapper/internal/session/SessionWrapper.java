/*
 * Copyright (C) 2011 Bosch Software Innovations GmbH. All rights reserved.
 */

package org.sourcepit.maven.wrapper.internal.session;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Disposable;

@Component(role = SessionWrapper.class)
public class SessionWrapper implements Disposable
{
   private AtomicBoolean isDisposed = new AtomicBoolean(false);

   @Requirement
   private List<ISessionListener> sessionListeners;

   public SessionWrapper()
   {
      super();
   }

   public void sessionAboutToStart(MavenSession session) throws MavenExecutionException
   {
      for (ISessionListener listener : sessionListeners)
      {
         listener.sessionAboutToStart(session);
      }
   }

   public void sessionEnded()
   {
      if (isDisposed.compareAndSet(false, true))
      {
         for (ISessionListener listener : sessionListeners)
         {
            listener.sessionEnded();
         }
      }
   }

   public void dispose()
   {
      sessionEnded();
   }
}
