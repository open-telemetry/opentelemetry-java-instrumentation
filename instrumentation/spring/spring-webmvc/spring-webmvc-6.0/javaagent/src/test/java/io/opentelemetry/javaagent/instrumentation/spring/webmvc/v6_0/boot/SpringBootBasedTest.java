/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webmvc.v6_0.boot;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.LOGIN;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.CodeAttributes.CODE_FUNCTION_NAME;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.spring.webmvc.v3_1.boot.AbstractSpringBootBasedTest;
import io.opentelemetry.instrumentation.spring.webmvc.v3_1.boot.AppConfig;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpData;
import io.opentelemetry.testing.internal.armeria.common.HttpHeaderNames;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import io.opentelemetry.testing.internal.armeria.common.QueryParams;
import io.opentelemetry.testing.internal.armeria.common.RequestHeadersBuilder;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

class SpringBootBasedTest extends AbstractSpringBootBasedTest {

  @RegisterExtension
  private static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

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
        Map.of(
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

  @Override
  protected AggregatedHttpRequest request(ServerEndpoint endpoint, String method) {
    AggregatedHttpRequest request = super.request(endpoint, method);
    if (endpoint != LOGIN || !"POST".equals(method)) {
      return request;
    }
    return AggregatedHttpRequest.of(prepareLoginHeaders(request.headers().toBuilder()).build());
  }

  private RequestHeadersBuilder prepareLoginHeaders(RequestHeadersBuilder headers) {
    AggregatedHttpResponse loginPage =
        client.execute(super.request(LOGIN, "GET")).aggregate().join();
    assertThat(loginPage.status().code()).isEqualTo(200);
    String sessionCookie =
        loginPage.headers().getAll(HttpHeaderNames.SET_COOKIE).stream()
            .filter(cookie -> cookie.startsWith("JSESSIONID="))
            .map(cookie -> cookie.split(";", 2)[0])
            .findFirst()
            .orElseThrow();
    Matcher csrfToken =
        Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(loginPage.contentUtf8());
    assertThat(csrfToken.find()).isTrue();

    // Receiving the page does not guarantee its server span has finished exporting.
    testing.waitForTraces(1);
    testing.clearData();

    return headers
        .set(HttpHeaderNames.COOKIE, sessionCookie)
        .set("X-CSRF-TOKEN", csrfToken.group(1));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "invalid"})
  void loginWithMissingOrInvalidCsrfTokenIsForbidden(String csrfToken) {
    RequestHeadersBuilder headers =
        prepareLoginHeaders(
            super.request(LOGIN, "POST").headers().toBuilder().contentType(MediaType.FORM_DATA));
    headers.remove("X-CSRF-TOKEN");
    if (!csrfToken.isEmpty()) {
      headers.set("X-CSRF-TOKEN", csrfToken);
    }
    QueryParams form = QueryParams.of("username", "test", "password", "password");
    AggregatedHttpRequest request =
        AggregatedHttpRequest.of(headers.build(), HttpData.ofUtf8(form.toQueryString()));

    AggregatedHttpResponse response = client.execute(request).aggregate().join();

    assertThat(response.status().code()).isEqualTo(403);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertServerSpan(span, "POST", LOGIN, 403),
                span ->
                    span.satisfies(
                            spanData -> assertThat(spanData.getName()).endsWith(".sendError"))
                        .hasKind(SpanKind.INTERNAL),
                errorPageSpanAssertions("POST", LOGIN).get(0)));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Override
  protected SpanDataAssert assertHandlerSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    if (testLatestDeps() && endpoint == ServerEndpoint.NOT_FOUND) {
      String handlerSpanName = "ResourceHttpRequestHandler.handleRequest";
      span.hasName(handlerSpanName)
          .hasKind(SpanKind.INTERNAL)
          .hasStatus(StatusData.error())
          .hasAttributesSatisfyingExactly(
              equalTo(
                  CODE_FUNCTION_NAME,
                  ResourceHttpRequestHandler.class.getName() + ".handleRequest"))
          .hasEventsSatisfyingExactly(
              event ->
                  event
                      .hasName("exception")
                      .hasAttributesSatisfyingExactly(
                          equalTo(
                              EXCEPTION_TYPE,
                              "org.springframework.web.servlet.resource.NoResourceFoundException"),
                          satisfies(EXCEPTION_MESSAGE, val -> val.isNotNull()),
                          satisfies(EXCEPTION_STACKTRACE, val -> val.isInstanceOf(String.class))));
      return span;
    } else {
      return super.assertHandlerSpan(span, method, endpoint);
    }
  }

  @Override
  protected SpanDataAssert assertResponseSpan(
      SpanDataAssert span, SpanData parentSpan, String method, ServerEndpoint endpoint) {
    if (testLatestDeps() && endpoint == ServerEndpoint.NOT_FOUND) {
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

  @Test
  void handlerMappingFilterResourceAvailable() {
    assertThat(
            getClass()
                .getClassLoader()
                .getResource(
                    "io/opentelemetry/javaagent/instrumentation/spring/webmvc/v6_0/OpenTelemetryHandlerMappingFilter.class"))
        .isNotNull();
  }
}
