/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.examples.springwar;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public final class LoaderListener implements ServletContextListener {
  @Override
  public void contextInitialized(ServletContextEvent event) {
    System.out.println("PROBE java.version=" + System.getProperty("java.version"));
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    while (loader != null) {
      System.out.println(
          "PROBE loader="
              + loader.getClass().getName()
              + "@"
              + Integer.toHexString(System.identityHashCode(loader)));
      loader = loader.getParent();
    }
  }

  @Override
  public void contextDestroyed(ServletContextEvent event) {}
}
