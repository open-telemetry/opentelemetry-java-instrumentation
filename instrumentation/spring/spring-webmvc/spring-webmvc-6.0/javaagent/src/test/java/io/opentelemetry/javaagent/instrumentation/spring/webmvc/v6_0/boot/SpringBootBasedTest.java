/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webmvc.v6_0.boot;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_FUNCTION;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_NAMESPACE;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.spring.webmvc.boot.AbstractSpringBootBasedTest;
import io.opentelemetry.instrumentation.spring.webmvc.boot.AppConfig;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

class SpringBootBasedTest extends AbstractSpringBootBasedTest {

  @RegisterExtension
  private static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  private static final boolean testLatestDeps = Boolean.getBoolean("testLatestDeps");

  private ConfigurableApplicationContext context;

  @Override
  protected ConfigurableApplicationContext context() {
    return context;
  }

  @Override
  protected Class<?> securityConfigClass() {
    return SecurityConfig.class;
  }

  @Override
  protected ConfigurableApplicationContext setupServer() {
    SpringApplication app = new SpringApplication(AppConfig.class, securityConfigClass());
    app.setDefaultProperties(
        ImmutableMap.of(
            "server.port",
            port,
            "server.context-path",
            getContextPath(),
            "server.servlet.contextPath",
            getContextPath(),
            "server.error.include-message",
            "always"));
    context = app.run();
    return context;
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Override
  protected SpanDataAssert assertHandlerSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    if (testLatestDeps && endpoint == ServerEndpoint.NOT_FOUND) {
      String handlerSpanName = "ResourceHttpRequestHandler.handleRequest";
      span.hasName(handlerSpanName)
          .hasKind(SpanKind.INTERNAL)
          .hasStatus(StatusData.error())
          .hasAttributesSatisfyingExactly(
              equalTo(CODE_NAMESPACE, ResourceHttpRequestHandler.class.getName()),
              equalTo(CODE_FUNCTION, "handleRequest"))
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
      // not verifying the parent span, in the latest version the responseSpan is the child of the
      // SERVER span, not the handler span
      return super.assertResponseSpan(span, method, endpoint);
    } else {
      return super.assertResponseSpan(span, parentSpan, method, endpoint);
    }
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setResponseCodeOnNonStandardHttpMethod(400);
    options.setExpectedException(new RuntimeException(EXCEPTION.getBody()));
  }
}
