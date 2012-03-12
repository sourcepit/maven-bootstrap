/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.maven.bootstrap.it;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 * @goal noop
 * @author Bernd Vogt <bernd.vogt@sourcepit.org>
 */
public class NoopMojo extends AbstractMojo
{
   public void execute() throws MojoExecutionException, MojoFailureException
   {
   }
}
