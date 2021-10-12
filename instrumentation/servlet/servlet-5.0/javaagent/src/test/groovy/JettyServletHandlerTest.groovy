/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import jakarta.servlet.Servlet
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ErrorHandler
import org.eclipse.jetty.servlet.ServletHandler
import spock.lang.IgnoreIf

@IgnoreIf({ !jvm.java11Compatible })
class JettyServletHandlerTest extends AbstractServlet5Test<Object, Object> {

  @Override
  String expectedServerSpanName(ServerEndpoint endpoint) {
    "HTTP GET"
  }

  @Override
  Object startServer(int port) {
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
  void addServlet(Object handlerObject, String path, Class<Servlet> servlet) {
    ServletHandler servletHandler = (ServletHandler) handlerObject
    servletHandler.addServletWithMapping(servlet, path)
  }

  @Override
  void stopServer(Object serverObject) {
    Server server = (Server) serverObject
    server.stop()
    server.destroy()
  }

  @Override
  String getContextPath() {
    ""
  }

  @Override
  Class<Servlet> servlet() {
    TestServlet5.Sync
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
