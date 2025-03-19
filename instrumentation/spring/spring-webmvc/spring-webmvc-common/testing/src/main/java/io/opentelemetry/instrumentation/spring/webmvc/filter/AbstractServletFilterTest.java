/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.filter;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_FUNCTION;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_NAMESPACE;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.springframework.context.ConfigurableApplicationContext;

public abstract class AbstractServletFilterTest
    extends AbstractHttpServerTest<ConfigurableApplicationContext> {

  protected abstract Class<?> securityConfigClass();

  protected abstract Class<?> filterConfigClass();

  @Override
  protected void stopServer(ConfigurableApplicationContext ctx) {
    ctx.close();
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setHasHandlerSpan(endpoint -> endpoint == NOT_FOUND);
    options.setHasErrorPageSpans(
        endpoint -> endpoint == ERROR || endpoint == EXCEPTION || endpoint == NOT_FOUND);
    options.setHasResponseSpan(
        endpoint -> endpoint == REDIRECT || endpoint == ERROR || endpoint == NOT_FOUND);
    options.setTestPathParam(true);
  }

  @Override
  protected SpanDataAssert assertResponseSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    if (endpoint == REDIRECT) {
      span.satisfies(spanData -> assertThat(spanData.getName()).endsWith(".sendRedirect"));
    } else if (endpoint == ERROR || endpoint == NOT_FOUND) {
      span.satisfies(spanData -> assertThat(spanData.getName()).endsWith(".sendError"));
    }
    span.hasKind(SpanKind.INTERNAL);
    return span;
  }

  @Override
  protected SpanDataAssert assertHandlerSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    String handlerSpanName = getHandlerSpanName(endpoint);
    if (endpoint == NOT_FOUND) {
      handlerSpanName = "ResourceHttpRequestHandler.handleRequest";
    }
    span.hasName(handlerSpanName).hasKind(SpanKind.INTERNAL);
    return span;
  }

  @Override
  public String expectedHttpRoute(ServerEndpoint endpoint, String method) {
    if (HttpConstants._OTHER.equals(method)) {
      return getContextPath() + endpoint.getPath();
    }
    switch (endpoint.name()) {
      case "PATH_PARAM":
        return getContextPath() + "/path/{id}/param";
      case "NOT_FOUND":
        return getContextPath() + "/**";
      default:
        return super.expectedHttpRoute(endpoint, method);
    }
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Override
  protected List<Consumer<SpanDataAssert>> errorPageSpanAssertions(
      String method, ServerEndpoint endpoint) {
    List<Consumer<SpanDataAssert>> spanAssertions = new ArrayList<>();
    spanAssertions.add(
        span ->
            span.hasName("BasicErrorController.error")
                .hasKind(SpanKind.INTERNAL)
                .hasAttributesSatisfyingExactly(
                    satisfies(CODE_NAMESPACE, v -> v.endsWith(".BasicErrorController")),
                    equalTo(CODE_FUNCTION, "error")));
    return spanAssertions;
  }

  private static String getHandlerSpanName(ServerEndpoint endpoint) {
    if (QUERY_PARAM.equals(endpoint)) {
      return "TestController.queryParam";
    } else if (PATH_PARAM.equals(endpoint)) {
      return "TestController.pathParam";
    } else if (CAPTURE_HEADERS.equals(endpoint)) {
      return "TestController.captureHeaders";
    } else if (INDEXED_CHILD.equals(endpoint)) {
      return "TestController.indexedChild";
    }
    return "TestController." + endpoint.name().toLowerCase(Locale.ROOT);
  }
}
