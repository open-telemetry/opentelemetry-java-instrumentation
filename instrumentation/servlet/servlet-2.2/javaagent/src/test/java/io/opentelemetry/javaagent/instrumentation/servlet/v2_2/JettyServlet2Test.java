/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v2_2;

import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.AUTH_REQUIRED;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_FUNCTION;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_NAMESPACE;

import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.jupiter.api.extension.RegisterExtension;

class JettyServlet2Test extends AbstractHttpServerTest<Server> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  private static final String CONTEXT = "ctx";

  @Override
  protected Server setupServer() throws Exception {
    Server jettyServer = new Server(port);
    for (Connector connector : jettyServer.getConnectors()) {
      connector.setHost("localhost");
    }
    ServletContextHandler servletContext = new ServletContextHandler(null, "/" + CONTEXT);
    servletContext.setErrorHandler(
        new ErrorHandler() {
          @Override
          protected void handleErrorPage(
              HttpServletRequest request, Writer writer, int code, String message)
              throws IOException {
            Throwable th = (Throwable) request.getAttribute("javax.servlet.error.exception");
            writer.write(th != null ? th.getMessage() : message);
          }
        });

    servletContext.addServlet(TestServlet2.Sync.class, SUCCESS.getPath());
    servletContext.addServlet(TestServlet2.Sync.class, QUERY_PARAM.getPath());
    servletContext.addServlet(TestServlet2.Sync.class, REDIRECT.getPath());
    servletContext.addServlet(TestServlet2.Sync.class, ERROR.getPath());
    servletContext.addServlet(TestServlet2.Sync.class, EXCEPTION.getPath());
    servletContext.addServlet(TestServlet2.Sync.class, AUTH_REQUIRED.getPath());
    servletContext.addServlet(TestServlet2.Sync.class, INDEXED_CHILD.getPath());

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
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setContextPath("/" + CONTEXT);
    options.setHttpAttributes(e -> new HashSet<>());
    options.setTestNotFound(false);
    // servlet 2 does not expose a way to retrieve response headers
    options.setTestCaptureHttpHeaders(false);
    options.setHasResponseSpan(e -> e.equals(REDIRECT) || e.equals(ERROR));
    options.setHasResponseCustomizer(e -> true);
  }

  @Override
  public String expectedServerSpanName(
      ServerEndpoint endpoint, String method, @Nullable String route) {
    if (method.equals(HttpConstants._OTHER)) {
      return "HTTP " + getContextPath() + endpoint.getPath();
    }

    if (NOT_FOUND.equals(endpoint)) {
      return method;
    } else if (PATH_PARAM.equals(endpoint)) {
      return method + " " + getContextPath() + "/path/:id/param";
    } else {
      return method + " " + getContextPath() + endpoint.getPath();
    }
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Override
  protected SpanDataAssert assertResponseSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    String responseMethod = endpoint.equals(REDIRECT) ? "sendRedirect" : "sendError";
    return span.hasName("Response." + responseMethod)
        .hasKind(INTERNAL)
        .hasAttributesSatisfyingExactly(
            equalTo(CODE_NAMESPACE, Response.class.getName()),
            equalTo(CODE_FUNCTION, responseMethod));
  }
}
