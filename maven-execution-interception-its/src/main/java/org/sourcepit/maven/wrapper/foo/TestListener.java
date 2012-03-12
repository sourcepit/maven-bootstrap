/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.maven.wrapper.foo;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.project.MavenProject;
import org.sourcepit.maven.wrapper.internal.session.BootstrapSession;
import org.sourcepit.maven.wrapper.internal.session.IMavenBootstrapperListener;

/**
 * @author Bernd Vogt <bernd.vogt@sourcepit.org>
 */
@Named
public class TestListener implements IMavenBootstrapperListener
{
   @Inject
   LegacySupport legacySupport;
   
   @Inject
   Map<String, Fooooo> fooMap;
   
   public void beforeProjectBuild(BootstrapSession session, MavenProject wrapperProject)
   {
      System.out.println("beforeProjectBuild");
   }

   public void afterProjectBuild(BootstrapSession session, MavenProject wrapperProject)
   {
      System.out.println("afterProjectBuild");
   }
}
