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
