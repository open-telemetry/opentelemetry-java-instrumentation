/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.jetty12;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.AbstractServlet5Test;
import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.TestServlet5;
import io.opentelemetry.semconv.HttpAttributes;
import jakarta.servlet.Servlet;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jetty.ee10.servlet.ServletHandler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.extension.RegisterExtension;

public class Jetty12ServletHandlerTest extends AbstractServlet5Test<Server, ServletHandler> {

  @RegisterExtension
  protected static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setContextPath("");
    options.setTestNotFound(false);
    options.setExpectedException(new IllegalStateException(EXCEPTION.getBody()));
    options.setHttpAttributes(
        serverEndpoint -> {
          Set<AttributeKey<?>> attributes =
              new HashSet<>(HttpServerTestOptions.DEFAULT_HTTP_ATTRIBUTES);
          attributes.remove(HttpAttributes.HTTP_ROUTE);
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
          public boolean handle(Request request, Response response, Callback callback) {
            String message =
                (String) request.getAttribute("org.eclipse.jetty.server.error_message");
            if (message != null) {
              response.write(true, StandardCharsets.UTF_8.encode(message), Callback.NOOP);
            }
            callback.succeeded();
            return true;
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
