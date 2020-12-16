/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat7

import io.opentelemetry.instrumentation.test.base.HttpServerTest
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

}
