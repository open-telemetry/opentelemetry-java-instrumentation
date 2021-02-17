/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.dropwizard.jetty.NonblockingServletHolder
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.servlet.ServletContainer

class JerseyHttpServerTest extends JaxRsHttpServerTest<Server> {

  @Override
  Server startServer(int port) {
    def servlet = new ServletContainer(ResourceConfig.forApplicationClass(JaxRsTestApplication))

    def handler = new ServletContextHandler(ServletContextHandler.SESSIONS)
    handler.setContextPath("/")
    handler.addServlet(new NonblockingServletHolder(servlet), "/*")

    def server = new Server(port)
    server.setHandler(handler)
    server.start()

    return server
  }

  @Override
  void stopServer(Server httpServer) {
    httpServer.stop()
  }

  @Override
  boolean asyncCancelHasSendError() {
    true
  }
}