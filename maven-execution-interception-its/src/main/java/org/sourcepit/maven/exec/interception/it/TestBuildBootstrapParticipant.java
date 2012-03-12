/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.maven.exec.interception.it;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Named;

import org.apache.maven.project.MavenProject;
import org.sourcepit.maven.exec.bootstrap.BootstrapSession;
import org.sourcepit.maven.exec.bootstrap.BuildBootstrapParticipant;

/**
 * @author Bernd Vogt <bernd.vogt@sourcepit.org>
 */
@Named
public class TestBuildBootstrapParticipant implements BuildBootstrapParticipant
{
   private Report report = new Report(new File(getClass().getName() + ".txt").getAbsoluteFile());

   public void beforeBuild(BootstrapSession bootstrapSession, MavenProject bootstrapProject)
   {
      final List<String> values = new ArrayList<String>();
      values.add("beforeBuild");
      values.add(bootstrapProject.getGroupId());
      values.add(bootstrapProject.getArtifactId());
      report.println(values);
   }

   public void afterBuild(BootstrapSession bootstrapSession, MavenProject bootstrapProject)
   {
      final List<String> values = new ArrayList<String>();
      values.add("afterBuild");
      values.add(bootstrapProject.getGroupId());
      values.add(bootstrapProject.getArtifactId());
      report.println(values);
   }
}
