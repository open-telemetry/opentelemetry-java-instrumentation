/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.struts.GreetingServlet
import org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.util.resource.FileResource

import javax.servlet.DispatcherType

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT

class Struts2ActionSpanTest extends HttpServerTest<Server> implements AgentTestTrait {

  @Override
  boolean testPathParam() {
    return true
  }

  @Override
  boolean testErrorBody() {
    return false
  }

  @Override
  boolean hasHandlerSpan(ServerEndpoint endpoint) {
    return endpoint != NOT_FOUND
  }

  @Override
  boolean hasResponseSpan(ServerEndpoint endpoint) {
    endpoint == REDIRECT || endpoint == ERROR || endpoint == EXCEPTION || endpoint == NOT_FOUND
  }

  @Override
  void responseSpan(TraceAssert trace, int index, Object controllerSpan, Object handlerSpan, String method, ServerEndpoint endpoint) {
    switch (endpoint) {
      case REDIRECT:
        redirectSpan(trace, index, handlerSpan)
        break
      case ERROR:
      case EXCEPTION:
      case NOT_FOUND:
        sendErrorSpan(trace, index, handlerSpan)
        break
    }
  }

  String expectedServerSpanName(ServerEndpoint endpoint) {
    switch (endpoint) {
      case PATH_PARAM:
        return getContextPath() + "/path/{id}/param"
      case NOT_FOUND:
        return getContextPath() + "/*"
      default:
        return endpoint.resolvePath(address).path
    }
  }

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, String method, ServerEndpoint endpoint) {
    trace.span(index) {
      name "GreetingAction.${endpoint.name().toLowerCase()}"
      kind INTERNAL
      if (endpoint == EXCEPTION) {
        status StatusCode.ERROR
        errorEvent(Exception, EXCEPTION.body)
      }
      def expectedMethodName = endpoint.name().toLowerCase()
      attributes {
        "${SemanticAttributes.CODE_NAMESPACE.key}" "io.opentelemetry.struts.GreetingAction"
        "${SemanticAttributes.CODE_FUNCTION.key}" expectedMethodName
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
    context.addServlet(GreetingServlet, "/greetingServlet")
    context.addFilter(StrutsPrepareAndExecuteFilter, "/*", EnumSet.of(DispatcherType.REQUEST))

    server.start()

    return server
  }

  @Override
  void stopServer(Server server) {
    server.stop()
    server.destroy()
  }

  // Struts runs from a servlet filter. Test that dispatching from struts action to a servlet
  // does not overwrite server span name given by struts instrumentation.
  def "test dispatch to servlet"() {
    setup:
    def response = client.get(address.resolve("dispatch").toString()).aggregate().join()

    expect:
    response.status().code() == 200
    response.contentUtf8() == "greeting"

    and:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name getContextPath() + "/dispatch"
          kind SpanKind.SERVER
          hasNoParent()
        }
        span(1) {
          name "GreetingAction.dispatch_servlet"
          kind INTERNAL
          childOf span(0)
        }
      }
    }
  }
}
