/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat7

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import org.apache.catalina.Context
import org.apache.catalina.startup.Tomcat

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
  Tomcat startServer(int port) {
    Tomcat tomcat = new Tomcat()
    tomcat.setBaseDir(File.createTempDir().absolutePath)
    tomcat.setPort(port)
    tomcat.getConnector()

    Context ctx = tomcat.addContext(getContextPath(), new File(".").getAbsolutePath())

    Tomcat.addServlet(ctx, "testServlet", new TestServlet())

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
