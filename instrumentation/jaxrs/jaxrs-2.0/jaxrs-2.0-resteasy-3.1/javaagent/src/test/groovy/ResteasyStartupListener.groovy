/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import org.jboss.resteasy.plugins.servlet.ResteasyServletInitializer
import test.JaxRsApplicationPathTestApplication

import javax.servlet.ServletContextEvent
import javax.servlet.ServletContextListener

// ServletContainerInitializer isn't automatically called due to the way this test is set up
// so we call it ourself
class ResteasyStartupListener implements ServletContextListener {
  @Override
  void contextInitialized(ServletContextEvent servletContextEvent) {
    new ResteasyServletInitializer().onStartup(Collections.singleton(JaxRsApplicationPathTestApplication),
      servletContextEvent.getServletContext())
  }

  @Override
  void contextDestroyed(ServletContextEvent servletContextEvent) {
  }
}
