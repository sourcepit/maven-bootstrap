/*******************************************************************************
 * Copyright (c) 2011 Bernd and others. All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Bernd - initial API and implementation and/or initial documentation
 *******************************************************************************/

package org.sourcepit.beef.maven.wrapper.internal.session;

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.eventspy.EventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionEvent.Type;
import org.apache.maven.execution.MavenExecutionResult;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

/**
 * @author Bernd
 */
@Component(role = EventSpy.class)
public class MavenEventSpy extends AbstractEventSpy
{
   @Requirement
   private SessionWrapper sessionWrapper;

   @Override
   public void onEvent(Object oEvent) throws Exception
   {
      if (oEvent instanceof ExecutionEvent)
      {
         final ExecutionEvent event = (ExecutionEvent) oEvent;
         if (Type.SessionEnded == event.getType())
         {
            sessionWrapper.sessionEnded();
         }
      }
      else if (oEvent instanceof MavenExecutionResult)
      {
         sessionWrapper.sessionEnded();
      }
   }

   @Override
   public void close() throws Exception
   {
      sessionWrapper.dispose();
   }
}
