/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import javax.servlet.Servlet
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ErrorHandler
import org.eclipse.jetty.servlet.ServletHandler

class JettyServletHandlerTest extends AbstractServlet3Test<Server, ServletHandler> {

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
