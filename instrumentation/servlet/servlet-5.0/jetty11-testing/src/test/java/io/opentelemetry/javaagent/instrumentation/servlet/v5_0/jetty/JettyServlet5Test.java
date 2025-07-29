/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.jetty;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.AbstractServlet5Test;
import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Writer;
import java.net.InetSocketAddress;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class JettyServlet5Test
    extends AbstractServlet5Test<Server, ServletContextHandler> {

  @RegisterExtension
  protected static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setTestNotFound(false);
    options.setContextPath("/jetty-context");
  }

  @Override
  protected Server setupServer() throws Exception {
    Server jettyServer = new Server(new InetSocketAddress("localhost", port));

    ServletContextHandler servletContext = new ServletContextHandler(null, getContextPath());
    servletContext.setErrorHandler(
        new ErrorHandler() {
          @Override
          protected void handleErrorPage(
              HttpServletRequest request, Writer writer, int code, String message)
              throws IOException {
            Throwable th = (Throwable) request.getAttribute("jakarta.servlet.error.exception");
            writer.write(th != null ? th.getMessage() : message);
          }
        });
    setupServlets(servletContext);
    jettyServer.setHandler(servletContext);

    jettyServer.start();

    return jettyServer;
  }

  @Override
  public void stopServer(Server server) throws Exception {
    server.stop();
    server.destroy();
  }

  @Override
  public void addServlet(
      ServletContextHandler servletContext, String path, Class<? extends Servlet> servlet) {
    servletContext.addServlet(servlet, path);
  }
}
