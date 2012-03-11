/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.maven.wrapper.internal.session;

import java.io.File;
import java.util.Collection;
import java.util.List;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.sourcepit.maven.wrapper.internal.session.AbstractMavenBootstrapper;
import org.sourcepit.maven.wrapper.internal.session.ISessionListener;

/**
 * @author Bernd Vogt <bernd.vogt@sourcepit.org>
 */
@Component(role = ISessionListener.class)
public class TestMavenBootstrapper extends AbstractMavenBootstrapper
{
   @Override
   protected void getModuleDescriptors(MavenSession session, Collection<File> descriptors,
      Collection<File> skippedDescritors) throws MavenExecutionException
   {
      System.out.println("getModuleDescriptors");
      final File baseDir = new File(session.getRequest().getBaseDirectory());
      descriptors.add(new File(baseDir, "pom.xml"));
   }

   @Override
   protected void beforeWrapperProjectsInitialized(MavenSession session, List<MavenProject> projects)
      throws MavenExecutionException
   {
      System.out.println("beforeWrapperProjectsInitialized");
   }

   @Override
   protected void afterWrapperProjectsInitialized(MavenSession session, List<MavenProject> projects)
      throws MavenExecutionException
   {
      System.out.println("afterWrapperProjectsInitialized");
   }
}
