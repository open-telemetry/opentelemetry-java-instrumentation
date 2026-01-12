/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jfinal;

import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static org.assertj.core.api.Assertions.assertThat;

import com.jfinal.core.JFinalFilter;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.code.SemconvCodeStabilityUtil;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.EnumSet;
import java.util.Locale;
import javax.servlet.DispatcherType;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.junit.jupiter.api.extension.RegisterExtension;

class JFinalTest extends AbstractHttpServerTest<Server> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  protected void configure(HttpServerTestOptions options) {
    options.setHasHandlerSpan(endpoint -> endpoint != NOT_FOUND);
    options.setHasResponseSpan(endpoint -> endpoint == REDIRECT);
    options.setExpectedHttpRoute(
        (endpoint, method) -> {
          if (endpoint == PATH_PARAM) {
            return "/path/123/param";
          }
          if (HttpConstants._OTHER.equals(method)) {
            return endpoint.getPath();
          }
          if (NOT_FOUND.equals(endpoint)) {
            return "/*";
          }
          return expectedHttpRoute(endpoint, method);
        });
  }

  @Override
  protected Server setupServer() throws Exception {
    Server server = new Server(port);

    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/");
    ServletHandler handler = new ServletHandler();

    FilterHolder fh =
        handler.addFilterWithMapping(
            JFinalFilter.class.getName(), "/*", EnumSet.of(DispatcherType.REQUEST));
    fh.setInitParameter("configClass", TestConfig.class.getName());

    context.addFilter(fh, "/*", EnumSet.of(DispatcherType.REQUEST));
    context.insertHandler(handler);
    server.setHandler(context);
    server.start();
    return server;
  }

  @Override
  protected void stopServer(Server server) throws Exception {
    server.stop();
  }

  @Override
  protected SpanDataAssert assertResponseSpan(
      SpanDataAssert span,
      SpanData serverSpan,
      SpanData controllerSpan,
      SpanData handlerSpan,
      String method,
      ServerEndpoint endpoint) {
    return span.satisfies(spanData -> assertThat(spanData.getName()).endsWith(".sendRedirect"))
        .hasParent(serverSpan)
        .hasKind(SpanKind.INTERNAL)
        .hasAttributesSatisfying(Attributes::isEmpty);
  }

  @Override
  public SpanDataAssert assertHandlerSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    span.hasName(getHandlerSpanName(endpoint))
        .hasKind(INTERNAL)
        .hasAttributesSatisfyingExactly(
            SemconvCodeStabilityUtil.codeFunctionAssertions(
                TestController.class, getHandlerMethod(endpoint)));

    if (endpoint == EXCEPTION) {
      span.hasStatus(StatusData.error());
      span.hasException(new IllegalStateException(EXCEPTION.getBody()));
    }
    return span;
  }

  private static String getHandlerMethod(ServerEndpoint endpoint) {
    if (QUERY_PARAM.equals(endpoint)) {
      return "query";
    } else if (PATH_PARAM.equals(endpoint)) {
      return "path";
    } else if (CAPTURE_HEADERS.equals(endpoint)) {
      return "captureHeaders";
    } else if (INDEXED_CHILD.equals(endpoint)) {
      return "child";
    }
    return endpoint.name().toLowerCase(Locale.ROOT);
  }

  private static String getHandlerSpanName(ServerEndpoint endpoint) {
    if (PATH_PARAM.equals(endpoint)) {
      return "TestController.pathParam";
    } else if (NOT_FOUND.equals(endpoint)) {
      return "jfinal.handle";
    } else if (ERROR.equals(endpoint)) {
      return "TestController.error";
    }
    return "TestController." + endpoint.getPath().replace("/", "");
  }
}
