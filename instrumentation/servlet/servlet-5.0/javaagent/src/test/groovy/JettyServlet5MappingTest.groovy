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
import spock.lang.IgnoreIf

@IgnoreIf({ !jvm.java11Compatible })
class JettyServlet5MappingTest extends AbstractServlet5MappingTest<Object, Object> {

  @Override
  Object startServer(int port) {
    Server server = new Server(port)
    ServletContextHandler handler = new ServletContextHandler(null, contextPath)
    setupServlets(handler)
    server.setHandler(handler)
    server.start()
    return server
  }

  @Override
  void stopServer(Object serverObject) {
    Server server = (Server) serverObject
    server.stop()
    server.destroy()
  }

  @Override
  protected void setupServlets(Object handlerObject) {
    ServletContextHandler handler = (ServletContextHandler) handlerObject
    super.setupServlets(handler)

    addServlet(handler, "/", DefaultServlet)
  }

  @Override
  void addServlet(Object handlerObject, String path, Class<Servlet> servlet) {
    ServletContextHandler handler = (ServletContextHandler) handlerObject
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
