/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.jetty;

import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.AbstractServlet5MappingTest;
import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;

class JettyServlet5MappingTest extends AbstractServlet5MappingTest<Server, ServletContextHandler> {

  @Override
  protected Server setupServer() throws Exception {
    Server server = new Server(port);
    ServletContextHandler handler = new ServletContextHandler(null, getContextPath());
    setupServlets(handler);
    server.setHandler(handler);
    server.start();
    return server;
  }

  @Override
  public void stopServer(Server server) throws Exception {
    server.stop();
    server.destroy();
  }

  @Override
  protected void setupServlets(ServletContextHandler handler) throws Exception {
    super.setupServlets(handler);

    addServlet(handler, "/", DefaultServlet.class);
  }

  @Override
  public void addServlet(
      ServletContextHandler servletContextHandler, String path, Class<? extends Servlet> servlet) {
    servletContextHandler.addServlet(servlet, path);
  }

  @Override
  public String getContextPath() {
    return "/jetty-context";
  }

  public static class DefaultServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
      response.sendError(404);
    }
  }
}
