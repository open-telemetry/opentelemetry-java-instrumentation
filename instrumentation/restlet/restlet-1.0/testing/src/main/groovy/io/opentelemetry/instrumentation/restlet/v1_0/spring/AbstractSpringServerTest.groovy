/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_0.spring

import io.opentelemetry.instrumentation.restlet.v1_0.AbstractRestletServerTest
import org.restlet.Component
import org.restlet.Router
import org.restlet.Server
import org.springframework.context.support.ClassPathXmlApplicationContext

abstract class AbstractSpringServerTest extends AbstractRestletServerTest {

  Router router

  abstract String getConfigurationName()

  @Override
  Server setupServer(Component component) {
    def context = new ClassPathXmlApplicationContext(getConfigurationName())
    router = (Router) context.getBean("testRouter")
    def server = (Server) context.getBean("testServer", "http", port)
    component.getServers().add(server)
    return server
  }

  @Override
  void setupRouting() {
    host.attach(router)
  }

}
