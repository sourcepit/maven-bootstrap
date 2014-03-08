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

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;


/**
 * @author Bernd
 */
@Named
public class MavenLifecycleParticipant extends AbstractMavenLifecycleParticipant
{
   @Inject
   private MavenExecutionInterceptor executionInterceptor;

   @Override
   public void afterSessionStart(MavenSession session) throws MavenExecutionException
   {
      super.afterSessionStart(session);
      executionInterceptor.onAfterSessionStart(session);
   }
}
