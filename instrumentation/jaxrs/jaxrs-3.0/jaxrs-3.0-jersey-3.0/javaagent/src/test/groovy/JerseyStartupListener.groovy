/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import jakarta.servlet.ServletContextEvent
import jakarta.servlet.ServletContextListener
import org.glassfish.jersey.servlet.init.JerseyServletContainerInitializer
import test.JaxRsApplicationPathTestApplication

// ServletContainerInitializer isn't automatically called due to the way this test is set up
// so we call it ourself
class JerseyStartupListener implements ServletContextListener {
  @Override
  void contextInitialized(ServletContextEvent servletContextEvent) {
    new JerseyServletContainerInitializer().onStartup(Collections.singleton(JaxRsApplicationPathTestApplication),
      servletContextEvent.getServletContext())
  }

  @Override
  void contextDestroyed(ServletContextEvent servletContextEvent) {
  }
}
