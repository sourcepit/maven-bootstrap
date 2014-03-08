/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.maven.bootstrap.it;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import javax.inject.Named;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.sourcepit.common.utils.file.FileUtils;
import org.sourcepit.common.utils.file.FileVisitor;
import org.sourcepit.common.utils.path.PathUtils;
import org.sourcepit.maven.bootstrap.core.AbstractBootstrapper;
import org.sourcepit.maven.exec.intercept.MavenExecutionParticipant;

/**
 * @author Bernd Vogt <bernd.vogt@sourcepit.org>
 */
@Named("TestMavenBootstrapper")
public class TestBootstrapper extends AbstractBootstrapper implements MavenExecutionParticipant
{
   private Report report = new Report(new File(getClass().getName() + ".txt").getAbsoluteFile());

   public TestBootstrapper()
   {
      super("org.sourcepit.tools", "maven-bootstrap-its");
   }

   @Override
   protected String getDependencyResolutionRequired()
   {
      return null;
   }

   @Override
   protected void adjustActualSession(MavenSession bootSession, MavenSession actualSession)
   {
      report.println("adjustActualSession");
   }

   @Override
   protected List<ArtifactRepository> filterArtifactRepositories(List<ArtifactRepository> remoteRepositories)
   {
      return null;
   }

   @Override
   protected void discoverProjectDescriptors(MavenSession session, final Collection<File> descriptors,
      Collection<File> skippedDescriptors)
   {
      report.println("discoverProjectDescriptors");

      final File baseDir = new File(session.getRequest().getBaseDirectory());
      FileUtils.accept(baseDir, new FileVisitor()
      {
         public boolean visit(File file)
         {
            final String fileName = file.getName();
            if (file.isDirectory() && !"target".equals(fileName))
            {
               final File pomFile = new File(file, "pom.xml");
               if (pomFile.exists())
               {
                  descriptors.add(pomFile);
                  return true;
               }
            }
            return false;
         }
      });

      final List<String> paths = new ArrayList<String>();
      for (File file : descriptors)
      {
         paths.add(PathUtils.getRelativePath(file.getAbsoluteFile(), baseDir, "/"));
      }
      report.println(paths);
   }

   @Override
   protected boolean isAllowExtensionExtensions(MavenSession bootSession, MavenProject bootProject)
   {
      final Properties properties = new Properties();
      properties.putAll(bootProject.getProperties());
      properties.putAll(bootSession.getSystemProperties()); // session wins
      properties.putAll(bootSession.getUserProperties());
      
      return Boolean.valueOf(properties.getProperty("allowExtensions", "false")).booleanValue();
   }

}
