/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsp;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.ClientAttributes;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.ExceptionAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.UserAgentAttributes;

class JspSpanAssertions {
  private final String baseUrl;
  private final int port;

  JspSpanAssertions(String baseUrl, int port) {
    this.baseUrl = baseUrl;
    this.port = port;
  }

  SpanDataAssert assertServerSpan(SpanDataAssert span, JspSpan spanData) {
    if (spanData.getExceptionClass() != null) {
      span.hasStatus(StatusData.error())
          .hasEventsSatisfyingExactly(
              event ->
                  event
                      .hasName("exception")
                      .hasAttributesSatisfyingExactly(
                          satisfies(
                              ExceptionAttributes.EXCEPTION_TYPE,
                              val ->
                                  val.satisfiesAnyOf(
                                      v -> val.isEqualTo(spanData.getExceptionClass().getName()),
                                      v ->
                                          val.contains(
                                              spanData.getExceptionClass().getSimpleName()))),
                          satisfies(
                              ExceptionAttributes.EXCEPTION_MESSAGE,
                              val ->
                                  val.satisfiesAnyOf(
                                      v -> assertThat(spanData.getErrorMessageOptional()).isTrue(),
                                      v -> val.isInstanceOf(String.class))),
                          satisfies(
                              ExceptionAttributes.EXCEPTION_STACKTRACE,
                              val -> val.isInstanceOf(String.class))));
    }

    return span.hasName(spanData.getMethod() + " " + spanData.getRoute())
        .hasNoParent()
        .hasKind(SpanKind.SERVER)
        .hasAttributesSatisfyingExactly(
            equalTo(UrlAttributes.URL_SCHEME, "http"),
            equalTo(UrlAttributes.URL_PATH, spanData.getRoute()),
            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, spanData.getMethod()),
            equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, spanData.getResponseStatus()),
            satisfies(
                UserAgentAttributes.USER_AGENT_ORIGINAL, val -> val.isInstanceOf(String.class)),
            equalTo(HttpAttributes.HTTP_ROUTE, spanData.getRoute()),
            equalTo(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "1.1"),
            equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
            equalTo(ServerAttributes.SERVER_PORT, port),
            equalTo(ClientAttributes.CLIENT_ADDRESS, "127.0.0.1"),
            equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"),
            satisfies(NetworkAttributes.NETWORK_PEER_PORT, val -> val.isInstanceOf(Long.class)),
            satisfies(
                ErrorAttributes.ERROR_TYPE,
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(spanData.getExceptionClass()).isNull(),
                        v -> assertThat(v).isEqualTo("500"))));
  }

  SpanDataAssert assertCompileSpan(SpanDataAssert span, JspSpan spanData) {
    if (spanData.getExceptionClass() != null) {
      span.hasStatus(StatusData.error())
          .hasEventsSatisfyingExactly(
              event ->
                  event
                      .hasName("exception")
                      .hasAttributesSatisfyingExactly(
                          equalTo(
                              ExceptionAttributes.EXCEPTION_TYPE,
                              spanData.getExceptionClass().getCanonicalName()),
                          satisfies(
                              ExceptionAttributes.EXCEPTION_STACKTRACE,
                              val -> val.isInstanceOf(String.class)),
                          satisfies(
                              ExceptionAttributes.EXCEPTION_MESSAGE,
                              val -> val.isInstanceOf(String.class))));
    }

    return span.hasName("Compile " + spanData.getRoute())
        .hasParent(spanData.getParent())
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("jsp.classFQCN"), "org.apache.jsp." + spanData.getClassName()),
            equalTo(stringKey("jsp.compiler"), "org.apache.jasper.compiler.JDTCompiler"));
  }

  SpanDataAssert assertRenderSpan(SpanDataAssert span, JspSpan spanData) {
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
                              ExceptionAttributes.EXCEPTION_TYPE,
                              val ->
                                  val.satisfiesAnyOf(
                                      v -> val.isEqualTo(spanData.getExceptionClass().getName()),
                                      v ->
                                          val.contains(
                                              spanData.getExceptionClass().getSimpleName()))),
                          satisfies(
                              ExceptionAttributes.EXCEPTION_MESSAGE,
                              val ->
                                  val.satisfiesAnyOf(
                                      v -> assertThat(spanData.getErrorMessageOptional()).isTrue(),
                                      v -> val.isInstanceOf(String.class))),
                          satisfies(
                              ExceptionAttributes.EXCEPTION_STACKTRACE,
                              val -> val.isInstanceOf(String.class))));
    }

    return span.hasName("Render " + spanData.getRoute())
        .hasParent(spanData.getParent())
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("jsp.requestURL"), baseUrl + requestUrl),
            satisfies(
                stringKey("jsp.forwardOrigin"),
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(spanData.getForwardOrigin()).isNull(),
                        v -> assertThat(v).isEqualTo(spanData.getForwardOrigin()))));
  }
}
