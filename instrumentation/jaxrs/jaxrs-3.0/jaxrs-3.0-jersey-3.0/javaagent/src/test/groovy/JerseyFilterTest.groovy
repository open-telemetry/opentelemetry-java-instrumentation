/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.base.HttpServerTestTrait
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse
import jakarta.ws.rs.core.Application
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.servlet.ServletContainer

import static Resource.Test1
import static Resource.Test2
import static Resource.Test3

class JerseyFilterTest extends JaxRsFilterTest implements HttpServerTestTrait<Server> {

  def setupSpec() {
    setupServer()
  }

  def cleanupSpec() {
    cleanupServer()
  }

  @Override
  Server startServer(int port) {
    def servlet = new ServletContainer(ResourceConfig.forApplication(new TestApplication()))

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
  boolean runsOnServer() {
    true
  }

  @Override
  String defaultServerSpanName() {
    "/*"
  }

  @Override
  def makeRequest(String path) {
    AggregatedHttpResponse response = client.post(address.resolve(path).toString(), "").aggregate().join()

    return [response.contentUtf8(), response.status().code()]
  }

  class TestApplication extends Application {
    @Override
    Set<Class<?>> getClasses() {
      def classes = new HashSet()
      classes.add(Test1)
      classes.add(Test2)
      classes.add(Test3)
      return classes
    }

    @Override
    Set<Object> getSingletons() {
      def singletons = new HashSet()
      singletons.add(simpleRequestFilter)
      singletons.add(prematchRequestFilter)
      return singletons
    }
  }
}