/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.maven.bootstrap.internal.core;

import static org.sourcepit.common.utils.io.IOResources.buffIn;
import static org.sourcepit.common.utils.io.IOResources.fileIn;
import static org.sourcepit.common.utils.io.IOResources.urlIn;
import static org.sourcepit.common.utils.io.IOResources.zipIn;
import static org.sourcepit.common.utils.xml.XmlUtils.queryNodes;
import static org.sourcepit.common.utils.xml.XmlUtils.readXml;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.sourcepit.common.utils.io.IOOperation;
import org.sourcepit.common.utils.io.IOResource;
import org.sourcepit.common.utils.io.ZipEntryResource;
import org.sourcepit.common.utils.lang.PipedIOException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class ExtensionDescriptorReader
{
   public static ExtensionDescriptor read(URL extensionArtifact)
   {
      final Document doc = readDocument(urlIn(extensionArtifact));
      return doc == null ? null : read(doc);
   }

   public static ExtensionDescriptor read(File extensionArtifact)
   {
      final Document doc = readDocument(fileIn(extensionArtifact));
      return doc == null ? null : read(doc);
   }

   private static Document readDocument(IOResource<? extends InputStream> resource)
   {
      final ZipEntryResource zipIn = zipIn(buffIn(resource), "META-INF/maven/extension.xml");
      
      final Document[] extensionDoc = new Document[1];

      IOOperation<InputStream> ioop = new IOOperation<InputStream>(zipIn)
      {
         @Override
         protected void run(InputStream openResource) throws IOException
         {
            extensionDoc[0] = readXml(openResource);
         }
      };
      try
      {
         ioop.run();
      }
      catch (PipedIOException e)
      {
         if (e.adapt(FileNotFoundException.class) == null)
         {
            throw e;
         }
      }

      return extensionDoc[0];
   }

   private static ExtensionDescriptor read(Document extensionDoc)
   {
      final ExtensionDescriptor extensionDescriptor = new ExtensionDescriptor();

      for (Node node : queryNodes(extensionDoc, "/extension/exportedPackages/exportedPackage"))
      {
         final Element elem = (Element) node;
         final String value = elem.getTextContent().trim();
         extensionDescriptor.getExportedPackages().add(value);
      }

      for (Node node : queryNodes(extensionDoc, "/extension/exportedArtifacts/exportedArtifact"))
      {
         final Element elem = (Element) node;
         final String value = elem.getTextContent().trim();
         extensionDescriptor.getExportedArtifacts().add(value);
      }

      return extensionDescriptor;
   }
}
