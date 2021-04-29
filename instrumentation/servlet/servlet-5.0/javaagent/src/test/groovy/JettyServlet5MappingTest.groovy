/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import jakarta.servlet.Servlet
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler

class JettyServlet5MappingTest extends AbstractServlet5MappingTest<Server, ServletContextHandler> {

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
