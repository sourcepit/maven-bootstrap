/*
 * Copyright (C) 2012 Bosch Software Innovations GmbH. All rights reserved.
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
