/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.maven.bootstrap.internal.core;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Test;
import org.sourcepit.maven.bootstrap.internal.core.PluginConfigurationReader;

public class PluginConfigurationReaderTest
{

   @Test
   public void test()
   {
      final Dependency foo = new Dependency();
      foo.setGroupId("foo.groupId");
      foo.setArtifactId("foo.groupId");
      foo.setVersion("foo.version");

      final Dependency bar = new Dependency();
      bar.setGroupId("bar.groupId");
      bar.setArtifactId("bar.artifactId");
      bar.setVersion("bar.version");
      bar.setClassifier("bar.classifier");
      bar.setType("bar.type");
      bar.setScope(Artifact.SCOPE_SYSTEM);
      bar.setSystemPath("bar.systemPath");

      Xpp3Dom extensions = new Xpp3Dom("extensions");
      extensions.addChild(toExtension(foo));
      extensions.addChild(toExtension(bar));

      Xpp3Dom configuration = new Xpp3Dom("configuration");
      configuration.addChild(extensions);

      Plugin plugin = new Plugin();
      plugin.setGroupId("org.sourcepit.b2");
      plugin.setArtifactId("b2-maven-plugin");
      plugin.setVersion("xxx");
      plugin.setConfiguration(configuration);

      MavenProject project = new MavenProject();
      project.getBuild().getPlugins().add(plugin);

      List<Dependency> extensionList = PluginConfigurationReader.readExtensions(project,
         "org.sourcepit.b2:b2-maven-plugin");
      assertEquals(2, extensionList.size());
      assertDependencyEquals(foo, extensionList.get(0));
      assertDependencyEquals(bar, extensionList.get(1));
   }
   
   private static void assertDependencyEquals(Dependency expected, Dependency actual)
   {
      assertEquals(expected.getGroupId(), actual.getGroupId());
      assertEquals(expected.getArtifactId(), actual.getArtifactId());
      assertEquals(expected.getVersion(), actual.getVersion());
      assertEquals(expected.getClassifier(), actual.getClassifier());
      assertEquals(expected.getType(), actual.getType());
      assertEquals(expected.getOptional(), actual.getOptional());
      assertEquals(expected.getScope(), actual.getScope());
      assertEquals(expected.getSystemPath(), actual.getSystemPath());
   }

   private Xpp3Dom toExtension(Dependency dependency)
   {
      final Xpp3Dom extension = new Xpp3Dom("extension");

      final Xpp3Dom groupId = new Xpp3Dom("groupId");
      groupId.setValue(dependency.getGroupId());
      extension.addChild(groupId);

      final Xpp3Dom artifactId = new Xpp3Dom("artifactId");
      artifactId.setValue(dependency.getArtifactId());
      extension.addChild(artifactId);

      final Xpp3Dom version = new Xpp3Dom("version");
      version.setValue(dependency.getVersion());
      extension.addChild(version);

      final Xpp3Dom classifier = new Xpp3Dom("classifier");
      classifier.setValue(dependency.getClassifier());
      extension.addChild(classifier);

      if (!"jar".equals(dependency.getType()))
      {
         final Xpp3Dom type = new Xpp3Dom("type");
         type.setValue(dependency.getType());
         extension.addChild(type);
      }

      final Xpp3Dom optional = new Xpp3Dom("optional");
      optional.setValue(dependency.getOptional());
      extension.addChild(optional);

      final Xpp3Dom scope = new Xpp3Dom("scope");
      scope.setValue(dependency.getScope());
      extension.addChild(scope);

      final Xpp3Dom systemPath = new Xpp3Dom("systemPath");
      systemPath.setValue(dependency.getSystemPath());
      extension.addChild(systemPath);

      return extension;
   }

}
