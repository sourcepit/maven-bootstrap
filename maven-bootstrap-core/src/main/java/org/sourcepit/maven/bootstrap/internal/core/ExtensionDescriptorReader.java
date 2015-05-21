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

import static org.sourcepit.common.utils.io.IO.buffIn;
import static org.sourcepit.common.utils.io.IO.fileIn;
import static org.sourcepit.common.utils.io.IO.urlIn;
import static org.sourcepit.common.utils.io.IO.zipIn;
import static org.sourcepit.common.utils.xml.XmlUtils.queryNodes;
import static org.sourcepit.common.utils.xml.XmlUtils.readXml;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.sourcepit.common.utils.io.IOHandle;
import org.sourcepit.common.utils.io.IOOperation;
import org.sourcepit.common.utils.io.handles.ZipInputStreamHandle;
import org.sourcepit.common.utils.lang.PipedIOException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class ExtensionDescriptorReader {
   public static ExtensionDescriptor read(URL extensionArtifact) {
      final Document doc = readDocument(urlIn(extensionArtifact));
      return doc == null ? null : read(doc);
   }

   public static ExtensionDescriptor read(File extensionArtifact) {
      final Document doc = readDocument(fileIn(extensionArtifact));
      return doc == null ? null : read(doc);
   }

   private static Document readDocument(IOHandle<? extends InputStream> resource) {
      final ZipInputStreamHandle zipIn = zipIn(buffIn(resource), "META-INF/maven/extension.xml");

      final Document[] extensionDoc = new Document[1];

      IOOperation<InputStream> ioop = new IOOperation<InputStream>(zipIn) {
         @Override
         protected void run(InputStream openResource) throws IOException {
            extensionDoc[0] = readXml(openResource);
         }
      };
      try {
         ioop.run();
      }
      catch (PipedIOException e) {
         if (e.adapt(FileNotFoundException.class) == null) {
            throw e;
         }
      }

      return extensionDoc[0];
   }

   private static ExtensionDescriptor read(Document extensionDoc) {
      final ExtensionDescriptor extensionDescriptor = new ExtensionDescriptor();

      for (Node node : queryNodes(extensionDoc, "/extension/exportedPackages/exportedPackage")) {
         final Element elem = (Element) node;
         final String value = elem.getTextContent().trim();
         extensionDescriptor.getExportedPackages().add(value);
      }

      for (Node node : queryNodes(extensionDoc, "/extension/exportedArtifacts/exportedArtifact")) {
         final Element elem = (Element) node;
         final String value = elem.getTextContent().trim();
         extensionDescriptor.getExportedArtifacts().add(value);
      }

      return extensionDescriptor;
   }
}
