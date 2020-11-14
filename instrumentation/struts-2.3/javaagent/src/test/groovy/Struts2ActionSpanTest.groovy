/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.sdk.trace.data.SpanData
import javax.servlet.DispatcherType
import org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.util.resource.FileResource

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM

class Struts2ActionSpanTest extends HttpServerTest<Server> {

  @Override
  boolean testNotFound() {
    return false
  }

  @Override
  boolean testPathParam() {
    return true
  }

  @Override
  boolean testExceptionBody() {
    return false
  }

  @Override
  boolean testError() {
    return false
  }

  @Override
  boolean testRedirect() {
    return false
  }

  @Override
  boolean hasHandlerSpan() {
    return true
  }

  String expectedServerSpanName(ServerEndpoint endpoint) {
    return endpoint == PATH_PARAM ? getContextPath() + "/path/{id}/param" : endpoint.resolvePath(address).path
  }

  String expectedHandlerName(ServerEndpoint serverEndpoint) {
    return "GreetingAction." + expectedMethodName(serverEndpoint)
  }

  String expectedMethodName(ServerEndpoint endpoint) {
    switch (endpoint) {
      case QUERY_PARAM: return "query"
      case EXCEPTION: return "exception"
      case PATH_PARAM: return "pathParam"
      default: return "success"
    }
  }

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, String method, ServerEndpoint endpoint) {
    trace.span(index) {
      name expectedHandlerName(endpoint)
      kind Span.Kind.INTERNAL
      errored endpoint == EXCEPTION
      if (endpoint == EXCEPTION) {
        errorEvent(Exception, EXCEPTION.body)
      }
      attributes {
        'code.namespace' "io.opentelemetry.struts.GreetingAction"
        'code.function' { it == expectedMethodName(endpoint) }
      }
      childOf((SpanData) parent)
    }
  }

  @Override
  String getContextPath() {
    return "/context"
  }

  @Override
  Server startServer(int port) {
    def server = new Server(port)
    ServletContextHandler context = new ServletContextHandler(0)
    context.setContextPath(getContextPath())
    def resource = new FileResource(getClass().getResource("/"))
    context.setBaseResource(resource)
    server.setHandler(context)

    context.addServlet(DefaultServlet, "/")
    context.addFilter(StrutsPrepareAndExecuteFilter, "/*", EnumSet.of(DispatcherType.REQUEST))

    server.start()

    return server
  }

  @Override
  void stopServer(Server server) {
    server.stop()
    server.destroy()
  }
}
