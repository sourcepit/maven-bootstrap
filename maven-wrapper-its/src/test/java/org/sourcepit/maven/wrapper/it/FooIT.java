/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.maven.wrapper.it;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import java.io.File;

import org.junit.Test;
import org.sourcepit.common.maven.testing.ExternalMavenTest;
import org.sourcepit.common.testing.Environment;

/**
 * @author Bernd Vogt <bernd.vogt@sourcepit.org>
 */
public class FooIT extends ExternalMavenTest
{
   @Override
   protected boolean isDebug()
   {
      return true;
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
   }
}
