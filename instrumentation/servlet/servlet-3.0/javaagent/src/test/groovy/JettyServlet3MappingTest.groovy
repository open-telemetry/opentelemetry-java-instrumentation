/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler

import javax.servlet.Servlet
import javax.servlet.ServletException
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class JettyServlet3MappingTest extends AbstractServlet3MappingTest<Server, ServletContextHandler> {

  @Override
  Server startServer(int port) {
    Server server = new Server(port)
    ServletContextHandler handler = new ServletContextHandler(null, contextPath)
    setupServlets(handler)
    server.setHandler(handler)
    server.start()
    return server
  }

  @Override
  void stopServer(Server server) {
    server.stop()
    server.destroy()
  }

  @Override
  protected void setupServlets(ServletContextHandler handler) {
    super.setupServlets(handler)

    addServlet(handler, "/", DefaultServlet)
  }

  @Override
  void addServlet(ServletContextHandler handler, String path, Class<Servlet> servlet) {
    handler.addServlet(servlet, path)
  }

  static class DefaultServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      response.sendError(404)
    }
  }

  @Override
  String getContextPath() {
    "/jetty-context"
  }
}
