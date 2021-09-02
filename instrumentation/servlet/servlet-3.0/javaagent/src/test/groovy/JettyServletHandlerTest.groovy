/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ErrorHandler
import org.eclipse.jetty.servlet.ServletHandler

import javax.servlet.Servlet
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION

class JettyServletHandlerTest extends AbstractServlet3Test<Server, ServletHandler> {

  private static final boolean IS_BEFORE_94 = isBefore94()

  static isBefore94() {
    def version = Server.getVersion().split("\\.")
    def major = Integer.parseInt(version[0])
    def minor = Integer.parseInt(version[1])
    return major < 9 || (major == 9 && minor < 4)
  }

  @Override
  boolean hasResponseSpan(ServerEndpoint endpoint) {
    return (IS_BEFORE_94 && endpoint == EXCEPTION) || super.hasResponseSpan(endpoint)
  }

  @Override
  void responseSpan(TraceAssert trace, int index, Object controllerSpan, Object handlerSpan, String method, ServerEndpoint endpoint) {
    if (IS_BEFORE_94 && endpoint == EXCEPTION) {
      sendErrorSpan(trace, index, handlerSpan)
    }
    super.responseSpan(trace, index, controllerSpan, handlerSpan, method, endpoint)
  }

  @Override
  String expectedServerSpanName(ServerEndpoint endpoint) {
    "HTTP GET"
  }

  @Override
  Server startServer(int port) {
    Server server = new Server(port)
    ServletHandler handler = new ServletHandler()
    server.setHandler(handler)
    setupServlets(handler)
    server.addBean(new ErrorHandler() {
      protected void handleErrorPage(HttpServletRequest request, Writer writer, int code, String message) throws IOException {
        Throwable th = (Throwable) request.getAttribute("javax.servlet.error.exception")
        writer.write(th ? th.message : message)
      }
    })
    server.start()
    return server
  }

  @Override
  void addServlet(ServletHandler servletHandler, String path, Class<Servlet> servlet) {
    servletHandler.addServletWithMapping(servlet, path)
  }

  @Override
  void stopServer(Server server) {
    server.stop()
    server.destroy()
  }

  @Override
  String getContextPath() {
    ""
  }

  @Override
  Class<Servlet> servlet() {
    TestServlet3.Sync
  }

  @Override
  boolean testNotFound() {
    false
  }

  @Override
  Class<?> expectedExceptionClass() {
    ServletException
  }
}
