/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.jetty;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.AbstractServlet3Test;
import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.TestServlet3;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.junit.jupiter.api.extension.RegisterExtension;

class JettyServletHandlerTest extends AbstractServlet3Test<Server, ServletHandler> {

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
  public boolean hasResponseSpan(ServerEndpoint endpoint) {
    return (JettyServlet3Test.IS_BEFORE_94 && endpoint.equals(EXCEPTION))
        || super.hasResponseSpan(endpoint);
  }

  @Override
  protected SpanDataAssert assertResponseSpan(
      SpanDataAssert span,
      SpanData controllerSpan,
      SpanData handlerSpan,
      String method,
      ServerEndpoint endpoint) {

    if (JettyServlet3Test.IS_BEFORE_94 && endpoint.equals(EXCEPTION)) {
      span.satisfies(it -> assertThat(it.getName()).matches(".*\\.sendError"))
          .hasKind(SpanKind.INTERNAL)
          .hasParent(handlerSpan);
    }

    return super.assertResponseSpan(span, controllerSpan, handlerSpan, method, endpoint);
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
            Throwable th = (Throwable) request.getAttribute("javax.servlet.error.exception");
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
    return TestServlet3.Sync.class;
  }
}
