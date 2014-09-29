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

package org.sourcepit.maven.bootstrap.it;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;

public class Report
{
   private final File file;

   public Report(File file)
   {
      this.file = file;
   }

   public List<String> readLines()
   {
      final List<String> lines = new ArrayList<String>();

      final BufferedReader reader = newReader();
      try
      {
         String line = reader.readLine();
         while (line != null)
         {
            lines.add(line);
            line = reader.readLine();
         }

      }
      catch (IOException e)
      {
         throw new IllegalStateException(e);
      }
      finally
      {
         IOUtils.closeQuietly(reader);
      }

      return lines;
   }

   public void println(Iterable<?> values)
   {
      final StringBuilder sb = new StringBuilder();
      for (Object value : values)
      {
         sb.append(value.toString());
         sb.append(',');
      }
      if (sb.length() > 0)
      {
         sb.deleteCharAt(sb.length() - 1);
      }
      println(sb);
   }

   public void println(Object value)
   {
      final Writer writer = newWriter();
      try
      {
         writer.write(value.toString());
         writer.write("\n");
      }
      catch (IOException e)
      {
         throw new IllegalStateException(e);
      }
      finally
      {
         IOUtils.closeQuietly(writer);
      }
   }

   private BufferedReader newReader()
   {
      try
      {
         if (!file.exists())
         {
            file.getParentFile().mkdirs();
            file.createNewFile();
         }
         return new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
      }
      catch (IOException e)
      {
         throw new IllegalStateException(e);
      }
   }

   private Writer newWriter()
   {
      try
      {
         if (!file.exists())
         {
            file.getParentFile().mkdirs();
            file.createNewFile();
         }
         return new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(file, true)), "UTF-8");
      }
      catch (IOException e)
      {
         throw new IllegalStateException(e);
      }
   }

}
