/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.jetty12;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.AbstractServlet5Test;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.IOException;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServletRequest;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class Jetty12Servlet5Test
    extends AbstractServlet5Test<Server, ServletContextHandler> {

  @RegisterExtension
  protected static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  static final boolean IS_BEFORE_94 = isBefore94();

  public static boolean isBefore94() {
    String[] version = Server.getVersion().split("\\.");
    int major = Integer.parseInt(version[0]);
    int minor = Integer.parseInt(version[1]);
    return major < 9 || (major == 9 && minor < 4);
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setTestNotFound(false);
    options.setExpectedException(new IllegalStateException(EXCEPTION.getBody()));
    options.setContextPath("/jetty-context");
    options.setVerifyServerSpanEndTime(!isAsyncTest());
  }

  @Override
  protected boolean hasResponseSpan(ServerEndpoint endpoint) {
    return (IS_BEFORE_94 && endpoint == EXCEPTION && !isAsyncTest())
        || super.hasResponseSpan(endpoint);
  }

  public boolean isAsyncTest() {
    return false;
  }

  @Override
  protected SpanDataAssert assertResponseSpan(
      SpanDataAssert span,
      SpanData controllerSpan,
      SpanData handlerSpan,
      String method,
      ServerEndpoint endpoint) {
    if (IS_BEFORE_94 && endpoint.equals(EXCEPTION)) {
      span.satisfies(it -> assertThat(it.getName()).matches(".*\\.sendError"))
          .hasKind(SpanKind.INTERNAL)
          .hasParent(handlerSpan);
    }

    return super.assertResponseSpan(span, controllerSpan, handlerSpan, method, endpoint);
  }

  @Override
  protected Server setupServer() throws Exception {
    Server jettyServer = new Server(new InetSocketAddress("localhost", port));

    ServletContextHandler servletContext = new ServletContextHandler(getContextPath());
    servletContext.setErrorHandler((request,response,callback) -> {
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
      ServletContextHandler servletContext, String path, Class<? extends Servlet> servlet)
      throws Exception {
    servletContext.addServlet(servlet, path);
  }
}
