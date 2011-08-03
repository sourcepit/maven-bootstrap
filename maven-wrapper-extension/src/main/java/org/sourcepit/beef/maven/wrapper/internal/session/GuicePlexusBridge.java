/*******************************************************************************
 * Copyright (c) 2011 Sonatype, Inc. All rights reserved. This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sourcepit.beef.maven.wrapper.internal.session;

import javax.inject.Inject;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.sonatype.guice.bean.binders.SpaceModule;
import org.sonatype.guice.bean.binders.WireModule;
import org.sonatype.guice.bean.locators.BeanLocator;
import org.sonatype.guice.bean.reflect.ClassSpace;
import org.sonatype.guice.bean.reflect.URLClassSpace;
import org.sonatype.guice.plexus.config.Strategies;
import org.sonatype.inject.BeanScanning;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.util.Providers;

/**
 * Example Guice/JSR330 <-> Plexus bridge component.
 */
@Component(role = GuicePlexusBridge.class, instantiationStrategy = Strategies.LOAD_ON_START)
public class GuicePlexusBridge implements Initializable
{
   /*
    * Sisu's JSR330 service-locator which provides a dynamic view over multiple Injectors.
    */
   @Inject
   private BeanLocator locator;

   public void initialize()
   {
      Guice.createInjector(new AbstractModule()
      {
         @Override
         protected void configure()
         {
            /*
             * Uses implicit injection of the instance to auto-register this Injector.
             */
            bind(BeanLocator.class).toInstance(locator);

            /*
             * ClassSpace is our abstraction of the concrete ClassLoader functionality.
             */
            ClassSpace space = new URLClassSpace(GuicePlexusBridge.class.getClassLoader());

            /*
             * WireModule automatically wires missing requirements via the BeanLocator.
             */
            install(new WireModule(new AbstractModule()
            {
               @Override
               protected void configure()
               {
                  /*
                   * Special hack for sisu-2.1.1, hide this space by binding it to null.
                   */
                  bind(ClassSpace.class).toProvider(Providers.<ClassSpace> of(null));
               }
            }, new SpaceModule(space, BeanScanning.CACHE /* use index as its faster */)));
         }
      });
   }

}
