/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat7

import static io.opentelemetry.api.trace.Span.Kind.INTERNAL
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.sdk.trace.data.SpanData
import org.apache.catalina.Context
import org.apache.catalina.startup.Tomcat
import org.apache.tomcat.util.descriptor.web.ErrorPage

class TomcatHandlerTest extends HttpServerTest<Tomcat> {

  def "Tomcat starts"() {
    expect:
    getServer() != null
  }

  @Override
  String getContextPath() {
    return "/app"
  }

  @Override
  Tomcat startServer(int port) {
    Tomcat tomcat = new Tomcat()
    tomcat.setBaseDir(File.createTempDir().absolutePath)
    tomcat.setPort(port)
    tomcat.getConnector()

    Context ctx = tomcat.addContext(getContextPath(), new File(".").getAbsolutePath())

    Tomcat.addServlet(ctx, "testServlet", new TestServlet())

    def errorPage = new ErrorPage()
    errorPage.setLocation("/errorPage")
    errorPage.setErrorCode(500)
    ctx.addErrorPage(errorPage)
    ctx.addServletMappingDecoded("/errorPage", "testServlet")

    // Mapping servlet to /* will result in all requests have a name of just a context.
    ServerEndpoint.values().each {
      ctx.addServletMappingDecoded(it.path, "testServlet")
    }
    tomcat.start()

    return tomcat
  }

  @Override
  void stopServer(Tomcat tomcat) {
    tomcat.getServer().stop()
  }

  @Override
  boolean testExceptionBody() {
    false
  }

  @Override
  boolean hasErrorPageSpans(ServerEndpoint endpoint) {
    endpoint == ERROR || endpoint == EXCEPTION
  }

  @Override
  void errorPageSpans(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      name "ApplicationDispatcher.forward"
      kind INTERNAL
      errored false
      childOf((SpanData) parent)
      attributes {
      }
    }
  }

  @Override
  boolean hasResponseSpan(ServerEndpoint endpoint) {
    endpoint == REDIRECT || endpoint == ERROR
  }

  @Override
  void responseSpan(TraceAssert trace, int index, Object parent, String method, ServerEndpoint endpoint) {
    switch (endpoint) {
      case REDIRECT:
        redirectSpan(trace, index, parent)
        break
      case ERROR:
        sendErrorSpan(trace, index, parent)
        break
    }
  }
}
