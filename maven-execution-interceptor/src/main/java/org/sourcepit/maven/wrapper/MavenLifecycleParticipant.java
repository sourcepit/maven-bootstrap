/*******************************************************************************
 * Copyright (c) 2011 Bernd and others. All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Bernd - initial API and implementation and/or initial documentation
 *******************************************************************************/

package org.sourcepit.maven.wrapper;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;


/**
 * @author Bernd
 */
@Component(role = AbstractMavenLifecycleParticipant.class)
public class MavenLifecycleParticipant extends AbstractMavenLifecycleParticipant
{
   @Requirement
   private MavenExecutionWrapper executionWrapper;

   @Override
   public void afterSessionStart(MavenSession session) throws MavenExecutionException
   {
      super.afterSessionStart(session);
      executionWrapper.onAfterSessionStart(session);
   }
}
