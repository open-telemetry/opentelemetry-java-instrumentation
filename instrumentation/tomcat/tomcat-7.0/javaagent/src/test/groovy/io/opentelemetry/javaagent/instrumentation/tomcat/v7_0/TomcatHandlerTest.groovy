/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v7_0

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.catalina.Context
import org.apache.catalina.connector.Request
import org.apache.catalina.connector.Response
import org.apache.catalina.core.StandardHost
import org.apache.catalina.startup.Tomcat
import org.apache.catalina.valves.ErrorReportValve

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT

class TomcatHandlerTest extends HttpServerTest<Tomcat> implements AgentTestTrait {

  def "Tomcat starts"() {
    expect:
    getServer() != null
  }

  @Override
  String getContextPath() {
    return "/app"
  }

  @Override
  String expectedServerSpanName(ServerEndpoint endpoint) {
    switch (endpoint) {
      case NOT_FOUND:
        return "HTTP GET"
      default:
        return endpoint.resolvePath(address).path
    }
  }

  @Override
  boolean testCapturedRequestParameters() {
    true
  }

  @Override
  Tomcat startServer(int port) {
    Tomcat tomcat = new Tomcat()
    tomcat.setBaseDir(File.createTempDir().absolutePath)
    tomcat.setPort(port)
    tomcat.getConnector()

    Context ctx = tomcat.addContext(getContextPath(), new File(".").getAbsolutePath())

    Tomcat.addServlet(ctx, "testServlet", new TestServlet())

    // Mapping servlet to /* will result in all requests have a name of just a context.
    ServerEndpoint.values().toList().stream()
      .filter { it != NOT_FOUND }
      .forEach {
        ctx.addServletMappingDecoded(it.path, "testServlet")
      }

    (tomcat.host as StandardHost).errorReportValveClass = ErrorHandlerValve.name

    tomcat.start()

    return tomcat
  }

  @Override
  void stopServer(Tomcat tomcat) {
    tomcat.getServer().stop()
  }

  @Override
  List<AttributeKey<?>> extraAttributes() {
    [
      SemanticAttributes.HTTP_SERVER_NAME,
      SemanticAttributes.NET_PEER_NAME,
      SemanticAttributes.NET_TRANSPORT
    ]
  }

  @Override
  boolean hasResponseSpan(ServerEndpoint endpoint) {
    endpoint == REDIRECT || endpoint == ERROR || endpoint == NOT_FOUND
  }

  @Override
  void responseSpan(TraceAssert trace, int index, Object parent, String method, ServerEndpoint endpoint) {
    switch (endpoint) {
      case REDIRECT:
        redirectSpan(trace, index, parent)
        break
      case ERROR:
      case NOT_FOUND:
        sendErrorSpan(trace, index, parent)
        break
    }
  }
}

class ErrorHandlerValve extends ErrorReportValve {
  @Override
  protected void report(Request request, Response response, Throwable t) {
    if (response.getStatus() < 400 || response.getContentWritten() > 0 || !response.setErrorReported()) {
      return
    }
    try {
      response.writer.print(t ? t.cause.message : response.message)
    } catch (IOException ignored) {
      // Ignore exception when writing exception message to response fails on IO - same as is done
      // by the superclass itself and by other built-in ErrorReportValve implementations.
    }
  }
}
