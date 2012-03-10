/*
 * Copyright (C) 2012 Bosch Software Innovations GmbH. All rights reserved.
 */

package org.sourcepit.maven.wrapper.internal.session;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.sonatype.guice.bean.locators.MutableBeanLocator;
import org.sonatype.inject.BeanEntry;

import com.google.inject.Key;

public class Foo
{
   @Inject
   private MutableBeanLocator locator;

   public List<Object> lookupList(ClassLoader classLoader, String fqn)
   {
      try
      {
         List<Object> result = new ArrayList<Object>();

         Class<?> clazz = classLoader.loadClass(fqn);

         Key<Object> key = (Key<Object>) Key.get(clazz);
         
         
         for (BeanEntry<Annotation, Object> beanEntry : locator.<Annotation, Object> locate(key))
         {
            result.add(beanEntry.getValue());
         }

         return result;
      }
      catch (ClassNotFoundException e)
      {
         throw new IllegalStateException(e);
      }
   }
}
