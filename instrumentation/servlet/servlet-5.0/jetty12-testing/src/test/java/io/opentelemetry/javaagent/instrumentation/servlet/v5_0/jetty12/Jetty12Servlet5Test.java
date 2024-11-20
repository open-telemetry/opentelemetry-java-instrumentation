/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.jetty12;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.AbstractServlet5Test;
import jakarta.servlet.Servlet;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class Jetty12Servlet5Test
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

    ServletContextHandler servletContext = new ServletContextHandler(getContextPath());
    servletContext.setErrorHandler(
        (request, response, callback) -> {
          String message = (String) request.getAttribute("org.eclipse.jetty.server.error_message");
          if (message != null) {
            response.write(true, StandardCharsets.UTF_8.encode(message), Callback.NOOP);
          }
          callback.succeeded();
          return true;
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
