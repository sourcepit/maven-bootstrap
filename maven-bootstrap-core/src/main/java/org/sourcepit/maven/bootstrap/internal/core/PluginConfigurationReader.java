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

package org.sourcepit.maven.bootstrap.internal.core;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

public final class PluginConfigurationReader {
   private PluginConfigurationReader() {
      super();
   }

   public static List<Dependency> readExtensions(MavenProject bootProject, String pluginKey) {
      final List<Dependency> result = new ArrayList<Dependency>();
      final Plugin plugin = bootProject.getPlugin(pluginKey);
      if (plugin != null) {
         final Xpp3Dom configuration = (Xpp3Dom) plugin.getConfiguration();
         if (configuration != null) {
            final Xpp3Dom extensions = configuration.getChild("extensions");
            if (extensions != null) {
               for (Xpp3Dom extension : extensions.getChildren("extension")) {
                  result.add(newDependency(extension));
               }
            }
         }
      }
      return result;
   }

   private static Dependency newDependency(Xpp3Dom dependency) {
      final Dependency result = new Dependency();
      result.setGroupId(extractNonEmptyValue(dependency.getChild("groupId")));
      result.setArtifactId(extractNonEmptyValue(dependency.getChild("artifactId")));
      result.setVersion(extractNonEmptyValue(dependency.getChild("version")));
      result.setClassifier(extractNonEmptyValue(dependency.getChild("classifier")));
      final String type = extractNonEmptyValue(dependency.getChild("type"));
      if (type != null) {
         result.setType(type);
      }
      result.setSystemPath(extractNonEmptyValue(dependency.getChild("systemPath")));
      result.setScope(extractNonEmptyValue(dependency.getChild("scope")));
      result.setOptional(extractNonEmptyValue(dependency.getChild("optional")));
      return result;
   }

   private static String extractNonEmptyValue(Xpp3Dom node) {
      String value = node == null ? null : node.getValue();
      if (value != null) {
         value.trim();
         if (value.length() == 0) {
            value = null;
         }
      }
      return value;
   }
}
