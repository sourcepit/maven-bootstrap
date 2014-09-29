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

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.junit.Test;
import org.sourcepit.common.maven.testing.ExternalMavenTest;
import org.sourcepit.common.testing.Environment;

/**
 * @author Bernd Vogt <bernd.vogt@sourcepit.org>
 */
public class BuildBootstrapperIT extends ExternalMavenTest
{
   @Override
   protected boolean isDebug()
   {
      return false;
   }

   @Override
   protected Environment newEnvironment()
   {
      return Environment.get("env-it.properties");
   }

   @Test
   public void testSimpleProject() throws Exception
   {
      final File projectDir = getResource("simple-project");

      final int error = build(projectDir, "-e", "-B", "compile");
      assertThat(error, is(0));

      Report bootstrapperReport = new Report(new File(projectDir, TestBootstrapper.class.getName() + ".txt"));
      List<String> lines = bootstrapperReport.readLines();
      assertThat(lines.size(), is(3));

      Iterator<String> it = lines.iterator();
      assertThat(it.next(), equalTo("discoverProjectDescriptors"));
      assertThat(it.next(), equalTo("pom.xml"));
      assertThat(it.next(), equalTo("adjustActualSession"));

      Report participantReport = new Report(new File(projectDir, TestBootstrapParticipant.class.getName() + ".txt"));
      lines = participantReport.readLines();
      assertThat(lines.size(), is(2));

      it = lines.iterator();
      assertThat(it.next(), equalTo("beforeBuild,org.sourcepit.it,simple-project"));
      assertThat(it.next(), equalTo("afterBuild,org.sourcepit.it,simple-project"));
   }

   @Test
   public void testReactorProject() throws Exception
   {
      final File projectDir = getResource("reactor-project");

      final int error = build(projectDir, "-e", "-B", "compile");
      assertThat(error, is(0));

      Report bootstrapperReport = new Report(new File(projectDir, TestBootstrapper.class.getName() + ".txt"));
      List<String> lines = bootstrapperReport.readLines();
      assertThat(lines.size(), is(3));

      Iterator<String> it = lines.iterator();
      assertThat(it.next(), equalTo("discoverProjectDescriptors"));

      // we musn't rely on module build ordering, it differs from system to system
      final List<String> poms = Arrays.asList(it.next().split(","));
      assertThat(poms.size(), is(3));
      assertThat(poms.contains("pom.xml"), is(true));
      assertThat(poms.contains("module-project-b/pom.xml"), is(true));
      assertThat(poms.contains("module-project-a/pom.xml"), is(true));

      assertThat(it.next(), equalTo("adjustActualSession"));


      Report participantReport = new Report(new File(projectDir, TestBootstrapParticipant.class.getName() + ".txt"));
      lines = participantReport.readLines();
      assertThat(lines.size(), is(4));

      it = lines.iterator();
      assertThat(it.next(), equalTo("beforeBuild,org.sourcepit.it,module-project-b"));
      assertThat(it.next(), equalTo("beforeBuild,org.sourcepit.it,module-project-a"));
      assertThat(it.next(), equalTo("afterBuild,org.sourcepit.it,module-project-b"));
      assertThat(it.next(), equalTo("afterBuild,org.sourcepit.it,module-project-a"));
   }

   @Test
   public void testExtensionExtension() throws Exception
   {
      final File projectDir = getResource("extension-extensions");

      final int error = build(projectDir, "-e", "-B", "compile", "-DallowExtensions=true");
      assertThat(error, is(0));

      Report bootstrapperReport = new Report(new File(projectDir, TestBootstrapper.class.getName() + ".txt"));
      List<String> lines = bootstrapperReport.readLines();
      assertThat(lines.size(), is(3));

      Iterator<String> it = lines.iterator();
      assertThat(it.next(), equalTo("discoverProjectDescriptors"));
      assertThat(it.next(), equalTo("pom.xml"));
      assertThat(it.next(), equalTo("adjustActualSession"));

      Report participantReport = new Report(new File(projectDir, TestBootstrapParticipant.class.getName() + ".txt"));
      lines = participantReport.readLines();
      assertThat(lines.size(), is(3));

      it = lines.iterator();
      assertThat(it.next(), equalTo("beforeBuild,org.sourcepit.it,extension-extensions"));
      assertThat(it.next(), equalTo(TestExtensionExtension.class.getName()));
      assertThat(it.next(), equalTo("afterBuild,org.sourcepit.it,extension-extensions"));
   }
}
