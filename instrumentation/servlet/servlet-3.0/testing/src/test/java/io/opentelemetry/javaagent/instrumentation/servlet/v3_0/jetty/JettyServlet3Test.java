/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.jetty;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.AbstractServlet3Test;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.IOException;
import java.io.Writer;
import java.net.InetSocketAddress;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class JettyServlet3Test
    extends AbstractServlet3Test<Server, ServletContextHandler> {

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

    ServletContextHandler servletContext = new ServletContextHandler(null, getContextPath());
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
