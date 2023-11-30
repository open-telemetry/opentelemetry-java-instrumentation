/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.api.internal.HttpConstants
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.servlet.ServletContainer
import test.JaxRsTestApplication

class JerseyHttpServerTest extends JaxRsHttpServerTest<Server> {

  @Override
  Server startServer(int port) {
    def servlet = new ServletContainer(ResourceConfig.forApplicationClass(JaxRsTestApplication))

    def handler = new ServletContextHandler(ServletContextHandler.SESSIONS)
    handler.setContextPath("/")
    handler.addServlet(new ServletHolder(servlet), "/*")

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

  @Override
  boolean testInterfaceMethodWithPath() {
    // disables a test that jersey deems invalid
    false
  }

  @Override
  String expectedHttpRoute(ServerEndpoint endpoint, String method) {
    if (method == HttpConstants._OTHER) {
      return "${getContextPath()}/*"
    }
    return super.expectedHttpRoute(endpoint, method)
  }

  @Override
  int getResponseCodeOnNonStandardHttpMethod() {
    500
  }
}
