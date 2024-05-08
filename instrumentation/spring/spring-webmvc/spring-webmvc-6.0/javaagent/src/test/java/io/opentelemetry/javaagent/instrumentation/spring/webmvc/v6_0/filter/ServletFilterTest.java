/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webmvc.v6_0.filter;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.spring.webmvc.filter.AbstractServletFilterTest;
import io.opentelemetry.instrumentation.spring.webmvc.filter.FilteredAppConfig;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.javaagent.instrumentation.spring.webmvc.v6_0.boot.SecurityConfig;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

class ServletFilterTest extends AbstractServletFilterTest {

  private static final boolean testLatestDeps = Boolean.getBoolean("testLatestDeps");

  @RegisterExtension
  private static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  @Override
  protected Class<?> securityConfigClass() {
    return SecurityConfig.class;
  }

  @Override
  protected Class<?> filterConfigClass() {
    return ServletFilterConfig.class;
  }

  @Override
  protected ConfigurableApplicationContext setupServer() {
    SpringApplication app =
        new SpringApplication(FilteredAppConfig.class, securityConfigClass(), filterConfigClass());
    app.setDefaultProperties(
        ImmutableMap.of("server.port", port, "server.error.include-message", "always"));
    return app.run();
  }

  @Override
  protected SpanDataAssert assertHandlerSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    if (testLatestDeps && endpoint == ServerEndpoint.NOT_FOUND) {
      String handlerSpanName = "ResourceHttpRequestHandler.handleRequest";
      span.hasName(handlerSpanName)
          .hasKind(SpanKind.INTERNAL)
          .hasStatus(StatusData.error())
          .hasEventsSatisfyingExactly(
              event ->
                  event
                      .hasName("exception")
                      .hasAttributesSatisfyingExactly(
                          equalTo(
                              EXCEPTION_TYPE,
                              "org.springframework.web.servlet.resource.NoResourceFoundException"),
                          satisfies(EXCEPTION_MESSAGE, val -> assertThat(val).isNotNull()),
                          satisfies(EXCEPTION_STACKTRACE, val -> val.isInstanceOf(String.class))));
      return span;
    } else {
      return super.assertHandlerSpan(span, method, endpoint);
    }
  }

  @Override
  protected SpanDataAssert assertResponseSpan(
      SpanDataAssert span, SpanData parentSpan, String method, ServerEndpoint endpoint) {
    if (testLatestDeps && endpoint == ServerEndpoint.NOT_FOUND) {
      span.satisfies(spanData -> assertThat(spanData.getName()).endsWith(".sendError"));
      span.hasKind(SpanKind.INTERNAL);
      // not verifying the parent span, in the latest version the responseSpan is the child of the
      // SERVER span, not the handler span
      return span;
    } else {
      return super.assertResponseSpan(span, parentSpan, method, endpoint);
    }
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setResponseCodeOnNonStandardHttpMethod(400);
  }
}
