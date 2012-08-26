/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.maven.bootstrap.internal.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.sourcepit.common.utils.io.IOResources.buffOut;
import static org.sourcepit.common.utils.io.IOResources.fileOut;
import static org.sourcepit.common.utils.io.IOResources.zipOut;
import static org.sourcepit.common.utils.xml.XmlUtils.newDocument;
import static org.sourcepit.common.utils.xml.XmlUtils.writeXml;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Rule;
import org.junit.Test;
import org.sourcepit.common.testing.Environment;
import org.sourcepit.common.testing.Workspace;
import org.sourcepit.common.utils.io.IOOperation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class ExtensionDescriptorReaderTest
{
   private final Environment env = Environment.get("env-test.properties");

   @Rule
   public Workspace ws = newWorkspace();

   protected Workspace newWorkspace()
   {
      return new Workspace(new File(env.getBuildDir(), "test-ws"), false);
   }

   @Test
   public void testNotExists()
   {
      final ExtensionDescriptor actual = ExtensionDescriptorReader.read(createExtensionArtifact(null));
      assertNull(actual);
   }

   @Test
   public void testEmpty()
   {
      final ExtensionDescriptor expected = new ExtensionDescriptor();

      final ExtensionDescriptor actual = ExtensionDescriptorReader.read(createExtensionArtifact(expected));

      assertEquals(expected.getExportedPackages(), actual.getExportedPackages());
      assertEquals(expected.getExportedArtifacts(), actual.getExportedArtifacts());
   }

   @Test
   public void testWithEntries()
   {
      final ExtensionDescriptor expected = new ExtensionDescriptor();
      expected.getExportedPackages().add("org.sourcepit");
      expected.getExportedPackages().add("foo.bar");

      expected.getExportedArtifacts().add("org.sourcepit:foo");

      final ExtensionDescriptor actual = ExtensionDescriptorReader.read(createExtensionArtifact(expected));

      assertEquals(expected.getExportedPackages(), actual.getExportedPackages());
      assertEquals(expected.getExportedArtifacts(), actual.getExportedArtifacts());
   }

   private File createExtensionArtifact(ExtensionDescriptor expected)
   {
      final File extensionArtifact = new File(ws.getRoot(), "extensionArtifact.zip");
      writeExtensionArtifact(extensionArtifact, expected);
      return extensionArtifact;
   }

   private static void writeExtensionArtifact(File extensionArtifact, final ExtensionDescriptor extensionDescriptor)
   {
      new IOOperation<ZipOutputStream>(zipOut(buffOut(fileOut(extensionArtifact))))
      {
         @Override
         protected void run(ZipOutputStream zipOut) throws IOException
         {
            if (extensionDescriptor == null)
            {
               final ZipEntry zipEntry = new ZipEntry("foo");
               zipOut.putNextEntry(zipEntry);
               zipOut.closeEntry();
            }
            else
            {
               final Document extensionDocument = toDocument(extensionDescriptor);
               final ZipEntry zipEntry = new ZipEntry("META-INF/maven/extension.xml");
               zipOut.putNextEntry(zipEntry);
               writeXml(extensionDocument, zipOut);
               zipOut.closeEntry();
            }
         }
      }.run();
   }

   private static Document toDocument(final ExtensionDescriptor extensionDescriptor)
   {
      Document extensionDoc = newDocument();

      Element extension = extensionDoc.createElement("extension");
      extensionDoc.appendChild(extension);

      if (!extensionDescriptor.getExportedPackages().isEmpty())
      {
         Element exportedPackages = extensionDoc.createElement("exportedPackages");
         extension.appendChild(exportedPackages);
         appendExportedPackages(exportedPackages, extensionDescriptor.getExportedPackages());
      }

      if (!extensionDescriptor.getExportedArtifacts().isEmpty())
      {
         Element exportedArtifacts = extensionDoc.createElement("exportedArtifacts");
         extension.appendChild(exportedArtifacts);
         appendExportedArtifacts(exportedArtifacts, extensionDescriptor.getExportedArtifacts());
      }

      return extensionDoc;
   }

   private static void appendExportedArtifacts(Element element, List<String> exportedArtifacts)
   {
      Document document = element.getOwnerDocument();
      for (String value : exportedArtifacts)
      {
         Element exportedArtifact = document.createElement("exportedArtifact");
         exportedArtifact.setTextContent(value);
         element.appendChild(exportedArtifact);
      }
   }

   private static void appendExportedPackages(Element element, List<String> exportedPackages)
   {
      Document document = element.getOwnerDocument();
      for (String value : exportedPackages)
      {
         Element exportedPackage = document.createElement("exportedPackage");
         exportedPackage.setTextContent(value);
         element.appendChild(exportedPackage);
      }
   }
}
