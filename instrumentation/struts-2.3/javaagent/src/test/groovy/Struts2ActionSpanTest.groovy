/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.struts.GreetingServlet
import javax.servlet.DispatcherType
import okhttp3.HttpUrl
import org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.util.resource.FileResource

class Struts2ActionSpanTest extends HttpServerTest<Server> implements AgentTestTrait {

  @Override
  boolean testNotFound() {
    return false
  }

  @Override
  boolean testPathParam() {
    return true
  }

  @Override
  boolean testErrorBody() {
    return false
  }

  @Override
  boolean hasHandlerSpan() {
    return true
  }

  @Override
  boolean hasResponseSpan(ServerEndpoint endpoint) {
    endpoint == REDIRECT || endpoint == ERROR || endpoint == EXCEPTION
  }

  @Override
  void responseSpan(TraceAssert trace, int index, Object controllerSpan, Object handlerSpan, String method, ServerEndpoint endpoint) {
    switch (endpoint) {
      case REDIRECT:
        redirectSpan(trace, index, handlerSpan)
        break
      case ERROR:
      case EXCEPTION:
        sendErrorSpan(trace, index, handlerSpan)
        break
    }
  }

  String expectedServerSpanName(ServerEndpoint endpoint) {
    return endpoint == PATH_PARAM ? getContextPath() + "/path/{id}/param" : endpoint.resolvePath(address).path
  }

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, String method, ServerEndpoint endpoint) {
    trace.span(index) {
      name "GreetingAction.${endpoint.name().toLowerCase()}"
      kind SpanKind.INTERNAL
      errored endpoint == EXCEPTION
      if (endpoint == EXCEPTION) {
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
    def url = HttpUrl.get(address.resolve("dispatch")).newBuilder()
      .build()
    def request = request(url, "GET", null).build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == 200
    response.body().string() == "greeting"

    and:
    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, getContextPath() + "/dispatch", null)
        basicSpan(it, 1, "GreetingAction.dispatch_servlet", span(0))
        basicSpan(it, 2, "Dispatcher.forward", span(0))
      }
    }
  }
}
