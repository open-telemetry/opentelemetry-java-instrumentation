/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import javax.servlet.DispatcherType
import org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.util.resource.FileResource

class Struts2ActionSpanTest extends HttpServerTest<Server> {

  @Override
  boolean testNotFound() {
    return false
  }

  @Override
  boolean testPathParam() {
    return false
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
  boolean controllerExceptionIsPropagatedToServer() {
    return false
  }

  @Override
  String expectedControllerName(ServerEndpoint serverEndpoint) {
    switch (serverEndpoint) {
      case ServerEndpoint.QUERY_PARAM: return "GreetingAction.query"
      case ServerEndpoint.EXCEPTION: return "GreetingAction.exception"
      default: return "GreetingAction.success"
    }
  }

  def "It records Struts action invocation as an internal span"() {
    expect:
    server != null

    when:
    def greeting = new String(new URL("http://localhost:${port}/success").openStream().readAllBytes())

    then:
    greeting == "success"
    assertTraces(1, {
      trace(0, 2, {
        span(0, {
          kind Span.Kind.SERVER
        })
        span(1, {
          name expectedControllerName()
          kind Span.Kind.INTERNAL
          attributes {
            "code.namespace" "GreetingAction"
            "code.function" "success"
          }
        })

      })
    })
  }

  @Override
  Server startServer(int port) {
    def server = new Server(port)
    ServletContextHandler context = new ServletContextHandler(0);
    context.setContextPath("/")
    def resource = new FileResource(getClass().getResource("/"))
    context.setBaseResource(resource)
    server.setHandler(context)

    context.addServlet(DefaultServlet.class, "/");
    context.addFilter(StrutsPrepareAndExecuteFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST))

    server.start()

    return server
  }

  @Override
  void stopServer(Server server) {
    server.stop()
    server.destroy()
  }
}
