/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.maven.bootstrap.internal.core;

import java.util.ArrayList;
import java.util.List;

public class ExtensionDescriptor
{
   private final List<String> exportedPackages = new ArrayList<String>();

   private final List<String> exportedArtifacts = new ArrayList<String>();

   public List<String> getExportedArtifacts()
   {
      return exportedArtifacts;
   }

   public List<String> getExportedPackages()
   {
      return exportedPackages;
   }
}
