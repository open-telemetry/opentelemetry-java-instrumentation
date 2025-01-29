/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.boot;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.AUTH_ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.LOGIN;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_FUNCTION;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_NAMESPACE;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpData;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import io.opentelemetry.testing.internal.armeria.common.QueryParams;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.web.util.OnCommittedResponseWrapper;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;
import org.springframework.web.servlet.view.RedirectView;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractSpringBootBasedTest
    extends AbstractHttpServerTest<ConfigurableApplicationContext> {

  protected abstract ConfigurableApplicationContext context();

  protected abstract Class<?> securityConfigClass();

  @Override
  protected void stopServer(ConfigurableApplicationContext ctx) {
    ctx.close();
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setContextPath("/xyz");
    options.setHasHandlerSpan(unused -> true);
    options.setHasResponseSpan(endpoint -> endpoint == REDIRECT || endpoint == NOT_FOUND);
    options.setTestPathParam(true);
    options.setHasErrorPageSpans(endpoint -> endpoint == NOT_FOUND);
    options.setHasRenderSpan(endpoint -> endpoint == REDIRECT);
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
      case "LOGIN":
        return getContextPath() + "/*";
      default:
        return super.expectedHttpRoute(endpoint, method);
    }
  }

  @Test
  void testSpansWithAuthError() {
    SavingAuthenticationProvider authProvider =
        context().getBean(SavingAuthenticationProvider.class);
    AggregatedHttpRequest request = request(AUTH_ERROR, "GET");

    authProvider.latestAuthentications.clear();
    AggregatedHttpResponse response = client.execute(request).aggregate().join();

    assertThat(response.status().code()).isEqualTo(401); // not secured

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> assertServerSpan(span, "GET", AUTH_ERROR, AUTH_ERROR.getStatus()),
                    span ->
                        span.satisfies(
                                spanData -> assertThat(spanData.getName()).endsWith(".sendError"))
                            .hasKind(SpanKind.INTERNAL),
                    span -> errorPageSpanAssertions(null, null)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"password", "dfsdföääöüüä", "🤓"})
  void testCharacterEncodingOfTestPassword(String testPassword) {
    SavingAuthenticationProvider authProvider =
        context().getBean(SavingAuthenticationProvider.class);

    QueryParams form = QueryParams.of("username", "test", "password", testPassword);
    AggregatedHttpRequest request =
        AggregatedHttpRequest.of(
            request(LOGIN, "POST").headers().toBuilder().contentType(MediaType.FORM_DATA).build(),
            HttpData.ofUtf8(form.toQueryString()));

    authProvider.latestAuthentications.clear();
    AggregatedHttpResponse response = client.execute(request).aggregate().join();

    assertThat(response.status().code()).isEqualTo(302); // redirect after success
    assertThat(authProvider.latestAuthentications.get(0).getPassword()).isEqualTo(testPassword);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> assertServerSpan(span, "POST", LOGIN, LOGIN.getStatus()),
                    span ->
                        span.satisfies(
                                spanData ->
                                    assertThat(spanData.getName()).endsWith(".sendRedirect"))
                            .hasKind(SpanKind.INTERNAL)));
  }

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

  @Override
  protected SpanDataAssert assertResponseSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    String methodName = endpoint == NOT_FOUND ? "sendError" : "sendRedirect";
    if (endpoint == NOT_FOUND) {
      span.satisfies(spanData -> assertThat(spanData.getName()).endsWith(".sendError"));
    } else {
      span.satisfies(spanData -> assertThat(spanData.getName()).endsWith(".sendRedirect"));
    }

    span.hasKind(SpanKind.INTERNAL)
        .hasAttributesSatisfyingExactly(
            satisfies(
                CODE_NAMESPACE,
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(v).isEqualTo(OnCommittedResponseWrapper.class.getName()),
                        v ->
                            assertThat(v)
                                .isEqualTo(
                                    "org.springframework.security.web.firewall.FirewalledResponse"),
                        v ->
                            assertThat(v)
                                .isEqualTo("jakarta.servlet.http.HttpServletResponseWrapper"))),
            equalTo(CODE_FUNCTION, methodName));
    return span;
  }

  @Override
  protected SpanDataAssert assertRenderSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    span.hasName("Render RedirectView")
        .hasKind(SpanKind.INTERNAL)
        .hasAttributesSatisfyingExactly(
            equalTo(
                AttributeKey.stringKey("spring-webmvc.view.type"), RedirectView.class.getName()));
    return span;
  }

  @Override
  protected SpanDataAssert assertHandlerSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    String handlerSpanName = getHandlerSpanName(endpoint);
    String codeNamespace = TestController.class.getName();
    if (endpoint == NOT_FOUND) {
      handlerSpanName = "ResourceHttpRequestHandler.handleRequest";
      codeNamespace = ResourceHttpRequestHandler.class.getName();
    }
    String codeFunction = handlerSpanName.substring(handlerSpanName.indexOf('.') + 1);
    span.hasName(handlerSpanName)
        .hasKind(SpanKind.INTERNAL)
        .hasAttributesSatisfyingExactly(
            equalTo(CODE_NAMESPACE, codeNamespace), equalTo(CODE_FUNCTION, codeFunction));
    if (endpoint == EXCEPTION) {
      span.hasStatus(StatusData.error());
      span.hasEventsSatisfyingExactly(
          event ->
              event
                  .hasName("exception")
                  .hasAttributesSatisfyingExactly(
                      equalTo(EXCEPTION_TYPE, "java.lang.RuntimeException"),
                      equalTo(EXCEPTION_MESSAGE, EXCEPTION.getBody()),
                      satisfies(EXCEPTION_STACKTRACE, val -> val.isInstanceOf(String.class))));
    }
    return span;
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
