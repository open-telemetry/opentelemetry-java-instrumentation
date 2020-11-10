/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.instrumentation.test.utils.PortUtils
import javax.servlet.DispatcherType
import org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.util.resource.FileResource
import spock.lang.Shared

class Struts2ActionSpanTest extends AgentTestRunner {

  @Shared
  Server server
  @Shared
  int port

  def "It records Struts action invocation as an internal span"() {
    expect:
    server != null

    when:
    def greeting = new String(new URL("http://localhost:${port}/greeting.action").openStream().readAllBytes())

    then:
    greeting == "hello"
    assertTraces(1, {
      trace(0, 1, {
        span(0, {
          name "GreetingAction.execute"
          kind Span.Kind.INTERNAL
          attributes {
            "code.namespace" "GreetingAction"
            "code.function" "execute"
          }
        })
      })
    })
  }

  void setup() {
    port = PortUtils.randomOpenPort()
    server = startServerWithStrutsApp(port)
  }

  void cleanup() {
    stopServer(server)
  }

  Server startServerWithStrutsApp(int port) {
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

  void stopServer(Server server) {
    server.stop()
  }
}
