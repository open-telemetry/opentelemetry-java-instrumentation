/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import hello.HelloApplication
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.base.HttpServerTestTrait
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse
import org.apache.wicket.protocol.http.WicketFilter
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.util.resource.FileResource
import org.jsoup.Jsoup

import javax.servlet.DispatcherType

class WicketTest extends AgentInstrumentationSpecification implements HttpServerTestTrait<Server> {

  @Override
  Server startServer(int port) {
    def server = new Server(port)
    ServletContextHandler context = new ServletContextHandler(0)
    context.setContextPath(getContextPath())
    def resource = new FileResource(getClass().getResource("/"))
    context.setBaseResource(resource)
    server.setHandler(context)

    context.addServlet(DefaultServlet, "/")
    def registration = context.getServletContext().addFilter("WicketApplication", WicketFilter)
    registration.setInitParameter("applicationClassName", HelloApplication.getName())
    registration.setInitParameter("filterMappingUrlPattern", "/wicket-test/*")
    registration.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, "/wicket-test/*")

    server.start()

    return server
  }

  @Override
  void stopServer(Server server) {
    server.stop()
    server.destroy()
  }

  @Override
  String getContextPath() {
    return "/jetty-context"
  }

  def "test hello"() {
    setup:
    AggregatedHttpResponse response = client.get(address.resolve("wicket-test/").toString()).aggregate().join()
    def doc = Jsoup.parse(response.contentUtf8())

    expect:
    response.status().code() == 200
    doc.selectFirst("#message").text() == "Hello World!"

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name getContextPath() + "/wicket-test/hello.HelloPage"
          kind SpanKind.SERVER
          hasNoParent()
        }
      }
    }
  }

  def "test exception"() {
    setup:
    AggregatedHttpResponse response = client.get(address.resolve("wicket-test/exception").toString()).aggregate().join()

    expect:
    response.status().code() == 500
    def ex = new Exception("test exception")

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name getContextPath() + "/wicket-test/hello.ExceptionPage"
          kind SpanKind.SERVER
          hasNoParent()
          status StatusCode.ERROR
          errorEvent(ex.class, ex.message)
        }
      }
    }
  }
}
