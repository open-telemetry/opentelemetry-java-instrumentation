/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.jetty;

import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.AbstractServlet5Test;
import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.TestServlet5;
import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.junit.jupiter.api.extension.RegisterExtension;

class JettyServletHandlerTest extends AbstractServlet5Test<Server, ServletHandler> {

  @RegisterExtension
  protected static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setContextPath("");
    options.setTestNotFound(false);
    options.setHttpAttributes(
        serverEndpoint -> {
          Set<AttributeKey<?>> attributes =
              new HashSet<>(HttpServerTestOptions.DEFAULT_HTTP_ATTRIBUTES);
          attributes.remove(HTTP_ROUTE);
          return attributes;
        });
  }

  @Override
  protected Server setupServer() throws Exception {
    Server server = new Server(port);
    ServletHandler handler = new ServletHandler();
    server.setHandler(handler);
    setupServlets(handler);
    server.addBean(
        new ErrorHandler() {
          @Override
          protected void handleErrorPage(
              HttpServletRequest request, Writer writer, int code, String message)
              throws IOException {
            Throwable th = (Throwable) request.getAttribute("jakarta.servlet.error.exception");
            writer.write(th != null ? th.getMessage() : message);
          }
        });
    server.start();
    return server;
  }

  @Override
  public void addServlet(
      ServletHandler servletHandler, String path, Class<? extends Servlet> servlet)
      throws Exception {
    servletHandler.addServletWithMapping(servlet, path);
  }

  @Override
  public void stopServer(Server server) throws Exception {
    server.stop();
    server.destroy();
  }

  @Override
  public Class<? extends Servlet> servlet() {
    return TestServlet5.Sync.class;
  }
}
