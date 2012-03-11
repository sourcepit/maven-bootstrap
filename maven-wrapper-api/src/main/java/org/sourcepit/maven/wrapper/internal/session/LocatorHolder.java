/*
 * Copyright (C) 2012 Bosch Software Innovations GmbH. All rights reserved.
 */

package org.sourcepit.maven.wrapper.internal.session;

import java.util.List;

import javax.inject.Inject;

import org.sonatype.guice.bean.locators.MutableBeanLocator;

public class LocatorHolder
{
   private MutableBeanLocator locator;

   @Inject
   private List<IMavenBootstrapperListener> listeners;
   
   public List<IMavenBootstrapperListener> getListeners()
   {
      return listeners;
   }
}
