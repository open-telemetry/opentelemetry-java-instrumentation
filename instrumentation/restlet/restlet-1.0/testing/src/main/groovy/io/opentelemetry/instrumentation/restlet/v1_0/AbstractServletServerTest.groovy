/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_0

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.util.resource.Resource
import org.eclipse.jetty.webapp.WebAppContext
import org.restlet.Application
import org.restlet.Restlet
import org.restlet.Router

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

abstract class AbstractServletServerTest extends HttpServerTest<Server> {

  @Override
  Server startServer(int port) {

    def webAppContext = new WebAppContext()
    webAppContext.setContextPath(getContextPath())

    webAppContext.setBaseResource(Resource.newSystemResource("servlet-ext-app"))

    def jettyServer = new Server(port)
    jettyServer.connectors.each {
      it.setHost('localhost')
    }

    jettyServer.setHandler(webAppContext)
    jettyServer.start()

    return jettyServer
  }

  @Override
  void stopServer(Server server) {
    server.stop()
    server.destroy()
  }

  @Override
  boolean testException() {
    false
  }

  @Override
  boolean testPathParam() {
    true
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(ServerEndpoint endpoint) {
    Set<AttributeKey<?>> extra = [
      SemanticAttributes.HTTP_SERVER_NAME
    ]
    super.httpAttributes(endpoint) + extra
  }

  @Override
  String expectedHttpRoute(ServerEndpoint endpoint) {
    switch (endpoint) {
      case PATH_PARAM:
        return getContextPath() + "/path/{id}/param"
      case NOT_FOUND:
        return getContextPath() + "/*"
      default:
        return super.expectedHttpRoute(endpoint)
    }
  }

  static class TestApp extends Application {

    @Override
    Restlet createRoot() {
      def router = new Router(getContext())

      router.attach(SUCCESS.path, RestletAppTestBase.SuccessResource)
      router.attach(REDIRECT.path, RestletAppTestBase.RedirectResource)
      router.attach(ERROR.path, RestletAppTestBase.ErrorResource)
      router.attach(EXCEPTION.path, RestletAppTestBase.ExceptionResource)
      router.attach("/path/{id}/param", RestletAppTestBase.PathParamResource)
      router.attach(QUERY_PARAM.path, RestletAppTestBase.QueryParamResource)
      router.attach(CAPTURE_HEADERS.path, RestletAppTestBase.CaptureHeadersResource)
      router.attach(INDEXED_CHILD.path, RestletAppTestBase.IndexedChildResource)

      return router
    }

  }
}
