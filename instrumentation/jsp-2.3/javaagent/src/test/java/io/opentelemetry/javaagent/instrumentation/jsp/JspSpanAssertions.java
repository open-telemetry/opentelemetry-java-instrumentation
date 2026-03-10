/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsp;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ClientAttributes.CLIENT_ADDRESS;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_PATH;
import static io.opentelemetry.semconv.UrlAttributes.URL_SCHEME;
import static io.opentelemetry.semconv.UserAgentAttributes.USER_AGENT_ORIGINAL;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import javax.annotation.Nullable;

class JspSpanAssertions {
  static final boolean isExperimentalEnabled =
      Boolean.getBoolean("otel.instrumentation.jsp.experimental-span-attributes");

  private final String baseUrl;
  private final int port;

  JspSpanAssertions(String baseUrl, int port) {
    this.baseUrl = baseUrl;
    this.port = port;
  }

  @Nullable
  public static String experimental(String value) {
    if (isExperimentalEnabled) {
      return value;
    }
    return null;
  }

  void assertServerSpan(SpanDataAssert span, JspSpan spanData) {
    if (spanData.getExceptionClass() != null) {
      span.hasStatus(StatusData.error())
          .hasEventsSatisfyingExactly(
              event ->
                  event
                      .hasName("exception")
                      .hasAttributesSatisfyingExactly(
                          satisfies(
                              EXCEPTION_TYPE,
                              val ->
                                  val.satisfiesAnyOf(
                                      v -> val.isEqualTo(spanData.getExceptionClass().getName()),
                                      v ->
                                          val.contains(
                                              spanData.getExceptionClass().getSimpleName()))),
                          satisfies(
                              EXCEPTION_MESSAGE,
                              val ->
                                  val.satisfiesAnyOf(
                                      v -> assertThat(spanData.getErrorMessageOptional()).isTrue(),
                                      v -> val.isInstanceOf(String.class))),
                          satisfies(EXCEPTION_STACKTRACE, val -> val.isInstanceOf(String.class))));
    }

    span.hasName(spanData.getMethod() + " " + spanData.getRoute())
        .hasNoParent()
        .hasKind(SpanKind.SERVER)
        .hasAttributesSatisfyingExactly(
            equalTo(URL_SCHEME, "http"),
            equalTo(URL_PATH, spanData.getRoute()),
            equalTo(HTTP_REQUEST_METHOD, spanData.getMethod()),
            equalTo(HTTP_RESPONSE_STATUS_CODE, spanData.getResponseStatus()),
            satisfies(USER_AGENT_ORIGINAL, val -> val.isInstanceOf(String.class)),
            equalTo(HTTP_ROUTE, spanData.getRoute()),
            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
            equalTo(SERVER_ADDRESS, "localhost"),
            equalTo(SERVER_PORT, port),
            equalTo(CLIENT_ADDRESS, "127.0.0.1"),
            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
            satisfies(NETWORK_PEER_PORT, val -> val.isInstanceOf(Long.class)),
            satisfies(
                ERROR_TYPE,
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(spanData.getExceptionClass()).isNull(),
                        v -> assertThat(v).isEqualTo("500"))));
  }

  void assertCompileSpan(SpanDataAssert span, JspSpan spanData) {
    if (spanData.getExceptionClass() != null) {
      span.hasStatus(StatusData.error())
          .hasEventsSatisfyingExactly(
              event ->
                  event
                      .hasName("exception")
                      .hasAttributesSatisfyingExactly(
                          equalTo(EXCEPTION_TYPE, spanData.getExceptionClass().getCanonicalName()),
                          satisfies(EXCEPTION_STACKTRACE, val -> val.isInstanceOf(String.class)),
                          satisfies(EXCEPTION_MESSAGE, val -> val.isInstanceOf(String.class))));
    }

    span.hasName("Compile " + spanData.getRoute())
        .hasParent(spanData.getParent())
        .hasAttributesSatisfyingExactly(
            equalTo(
                stringKey("jsp.classFQCN"),
                experimental("org.apache.jsp." + spanData.getClassName())),
            equalTo(
                stringKey("jsp.compiler"), experimental("org.apache.jasper.compiler.JDTCompiler")));
  }

  void assertRenderSpan(SpanDataAssert span, JspSpan spanData) {
    String requestUrl = spanData.getRoute();
    if (spanData.getRequestUrlOverride() != null) {
      requestUrl = spanData.getRequestUrlOverride();
    }

    if (spanData.getExceptionClass() != null) {
      span.hasStatus(StatusData.error())
          .hasEventsSatisfyingExactly(
              event ->
                  event
                      .hasName("exception")
                      .hasAttributesSatisfyingExactly(
                          satisfies(
                              EXCEPTION_TYPE,
                              val ->
                                  val.satisfiesAnyOf(
                                      v -> val.isEqualTo(spanData.getExceptionClass().getName()),
                                      v ->
                                          val.contains(
                                              spanData.getExceptionClass().getSimpleName()))),
                          satisfies(
                              EXCEPTION_MESSAGE,
                              val ->
                                  val.satisfiesAnyOf(
                                      v -> assertThat(spanData.getErrorMessageOptional()).isTrue(),
                                      v -> val.isInstanceOf(String.class))),
                          satisfies(EXCEPTION_STACKTRACE, val -> val.isInstanceOf(String.class))));
    }

    span.hasName("Render " + spanData.getRoute()).hasParent(spanData.getParent());

    if (isExperimentalEnabled) {
      span.hasAttributesSatisfyingExactly(
          equalTo(stringKey("jsp.requestURL"), baseUrl + requestUrl),
          satisfies(
              stringKey("jsp.forwardOrigin"),
              val ->
                  val.satisfiesAnyOf(
                      v -> assertThat(spanData.getForwardOrigin()).isNull(),
                      v -> assertThat(v).isEqualTo(spanData.getForwardOrigin()))));
    }
  }
}
