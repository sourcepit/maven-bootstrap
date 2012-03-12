/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.maven.exec.interception.it;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.sourcepit.common.utils.file.FileUtils;
import org.sourcepit.common.utils.file.FileVisitor;
import org.sourcepit.common.utils.path.PathUtils;
import org.sourcepit.maven.exec.bootstrap.AbstractBuildBootstrapper;
import org.sourcepit.maven.exec.participate.MavenExecutionParticipant;

/**
 * @author Bernd Vogt <bernd.vogt@sourcepit.org>
 */
@Component(role = MavenExecutionParticipant.class, hint = "TestMavenBootstrapper")
public class TestBuildBootstrapper extends AbstractBuildBootstrapper
{
   private Report report = new Report(new File(getClass().getName() + ".txt").getAbsoluteFile());

   @Override
   protected void getModuleDescriptors(MavenSession session, final Collection<File> descriptors,
      Collection<File> skippedDescritors) throws MavenExecutionException
   {
      report.println("getModuleDescriptors");

      final File baseDir = new File(session.getRequest().getBaseDirectory());
      FileUtils.accept(baseDir, new FileVisitor()
      {
         public boolean visit(File file)
         {
            final String fileName = file.getName();
            if (file.isFile() && "pom.xml".equals(fileName))
            {
               descriptors.add(file);
            }
            return file.isDirectory() && !"target".equals(fileName);
         }
      });

      final List<String> paths = new ArrayList<String>();
      for (File file : descriptors)
      {
         paths.add(PathUtils.getRelativePath(file.getAbsoluteFile(), baseDir, File.separator));
      }
      report.println(paths);
   }

   @Override
   protected void beforeBootstrapProjects(MavenSession session, List<MavenProject> projects)
      throws MavenExecutionException
   {
      report.println("beforeBootstrapProjects");
   }

   @Override
   protected void afterWrapperProjectsInitialized(MavenSession session, List<MavenProject> projects)
      throws MavenExecutionException
   {
      report.println("afterWrapperProjectsInitialized");
   }

}
