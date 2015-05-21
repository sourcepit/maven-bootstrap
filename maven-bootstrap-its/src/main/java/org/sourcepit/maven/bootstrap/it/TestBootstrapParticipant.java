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

package org.sourcepit.maven.bootstrap.it;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.sourcepit.maven.bootstrap.participation.BootstrapParticipant;

/**
 * @author Bernd Vogt <bernd.vogt@sourcepit.org>
 */
@Named
public class TestBootstrapParticipant implements BootstrapParticipant {
   private Report report = new Report(new File(getClass().getName() + ".txt").getAbsoluteFile());

   @Inject
   private List<ExtensionExtension> extensionExtensions;

   public void beforeBuild(MavenSession bootSession, MavenProject bootProject, MavenSession actualSession) {
      final List<String> values = new ArrayList<String>();
      values.add("beforeBuild");
      values.add(bootProject.getGroupId());
      values.add(bootProject.getArtifactId());

      report.println(values);

      for (ExtensionExtension ext : extensionExtensions) {
         report.println(ext.getClass().getName());
      }
   }

   public void afterBuild(MavenSession bootSession, MavenProject bootProject, MavenSession actualSession) {
      final List<String> values = new ArrayList<String>();
      values.add("afterBuild");
      values.add(bootProject.getGroupId());
      values.add(bootProject.getArtifactId());
      report.println(values);
   }
}
