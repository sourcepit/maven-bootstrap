/*******************************************************************************
 * Copyright (c) 2011 Bernd and others. All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Bernd - initial API and implementation and/or initial documentation
 *******************************************************************************/

package org.sourcepit.maven.exec.internal.intercept;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.eventspy.EventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenExecutionResult;


/**
 * @author Bernd
 */
@Named
public class MavenEventSpy extends AbstractEventSpy implements EventSpy
{
   @Inject
   private MavenExecutionInterceptor executionInterceptor;

   @Override
   public void onEvent(Object oEvent) throws Exception
   {
      if (oEvent instanceof ExecutionEvent)
      {
         final ExecutionEvent event = (ExecutionEvent) oEvent;
         if (ExecutionEvent.Type.SessionEnded == event.getType())
         {
            executionInterceptor.onSessionEnded(event.getSession());
         }
      }
      else if (oEvent instanceof MavenExecutionResult)
      {
         executionInterceptor.onMavenExecutionResult((MavenExecutionResult) oEvent);
      }
   }
}
