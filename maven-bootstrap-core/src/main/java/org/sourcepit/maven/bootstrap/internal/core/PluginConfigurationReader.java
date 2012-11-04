/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.maven.bootstrap.internal.core;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

public final class PluginConfigurationReader
{
   private PluginConfigurationReader()
   {
      super();
   }

   public static List<Dependency> readExtensions(MavenProject bootProject, String pluginKey)
   {
      final List<Dependency> result = new ArrayList<Dependency>();
      final Plugin plugin = bootProject.getPlugin(pluginKey);
      if (plugin != null)
      {
         final Xpp3Dom configuration = (Xpp3Dom) plugin.getConfiguration();
         if (configuration != null)
         {
            final Xpp3Dom extensions = configuration.getChild("extensions");
            if (extensions != null)
            {
               for (Xpp3Dom extension : extensions.getChildren("extension"))
               {
                  result.add(newDependency(extension));
               }
            }
         }
      }
      return result;
   }

   private static Dependency newDependency(Xpp3Dom dependency)
   {
      final Dependency result = new Dependency();
      result.setGroupId(extractNonEmptyValue(dependency.getChild("groupId")));
      result.setArtifactId(extractNonEmptyValue(dependency.getChild("artifactId")));
      result.setVersion(extractNonEmptyValue(dependency.getChild("version")));
      result.setClassifier(extractNonEmptyValue(dependency.getChild("classifier")));
      final String type = extractNonEmptyValue(dependency.getChild("type"));
      if (type != null)
      {
         result.setType(type);
      }
      result.setSystemPath(extractNonEmptyValue(dependency.getChild("systemPath")));
      result.setScope(extractNonEmptyValue(dependency.getChild("scope")));
      result.setOptional(extractNonEmptyValue(dependency.getChild("optional")));
      return result;
   }

   private static String extractNonEmptyValue(Xpp3Dom node)
   {
      String value = node == null ? null : node.getValue();
      if (value != null)
      {
         value.trim();
         if (value.length() == 0)
         {
            value = null;
         }
      }
      return value;
   }
}
