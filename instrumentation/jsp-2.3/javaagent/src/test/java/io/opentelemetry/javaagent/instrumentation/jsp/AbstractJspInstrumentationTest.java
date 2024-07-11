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
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.ClientAttributes;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.ExceptionAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.UserAgentAttributes;

interface AbstractJspInstrumentationTest {
  String getBaseUrl();

  int getPort();

  default SpanDataAssert assertServerSpan(SpanDataAssert span, JspSpanAssertion spanAssertion) {
    if (spanAssertion.getExceptionClass() != null) {
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
                                      v ->
                                          val.isEqualTo(
                                              spanAssertion.getExceptionClass().getName()),
                                      v ->
                                          val.contains(
                                              spanAssertion.getExceptionClass().getSimpleName()))),
                          satisfies(
                              ExceptionAttributes.EXCEPTION_MESSAGE,
                              val -> val.isInstanceOf(String.class)),
                          satisfies(
                              ExceptionAttributes.EXCEPTION_STACKTRACE,
                              val -> val.isInstanceOf(String.class))));
    }

    return span.hasName(spanAssertion.getMethod() + " " + spanAssertion.getRoute())
        .hasNoParent()
        .hasKind(SpanKind.SERVER)
        .hasAttributesSatisfyingExactly(
            equalTo(UrlAttributes.URL_SCHEME, "http"),
            equalTo(UrlAttributes.URL_PATH, spanAssertion.getRoute()),
            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, spanAssertion.getMethod()),
            equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, spanAssertion.getResponseStatus()),
            satisfies(
                UserAgentAttributes.USER_AGENT_ORIGINAL, val -> val.isInstanceOf(String.class)),
            equalTo(HttpAttributes.HTTP_ROUTE, spanAssertion.getRoute()),
            equalTo(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "1.1"),
            equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
            equalTo(ServerAttributes.SERVER_PORT, getPort()),
            equalTo(ClientAttributes.CLIENT_ADDRESS, "127.0.0.1"),
            equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"),
            satisfies(NetworkAttributes.NETWORK_PEER_PORT, val -> val.isInstanceOf(Long.class)),
            satisfies(
                ErrorAttributes.ERROR_TYPE,
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(spanAssertion.getExceptionClass()).isNull(),
                        v -> assertThat(v).isEqualTo("500"))));
  }

  default SpanDataAssert assertCompileSpan(SpanDataAssert span, JspSpanAssertion spanAssertion) {
    if (spanAssertion.getExceptionClass() != null) {
      span.hasStatus(StatusData.error())
          .hasEventsSatisfyingExactly(
              event ->
                  event
                      .hasName("exception")
                      .hasAttributesSatisfyingExactly(
                          equalTo(
                              ExceptionAttributes.EXCEPTION_TYPE,
                              spanAssertion.getExceptionClass().getCanonicalName()),
                          satisfies(
                              ExceptionAttributes.EXCEPTION_STACKTRACE,
                              val -> val.isInstanceOf(String.class)),
                          satisfies(
                              ExceptionAttributes.EXCEPTION_MESSAGE,
                              val -> val.isInstanceOf(String.class))));
    }

    return span.hasName("Compile " + spanAssertion.getRoute())
        .hasParent(spanAssertion.getParent())
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("jsp.classFQCN"), "org.apache.jsp." + spanAssertion.getClassName()),
            equalTo(stringKey("jsp.compiler"), "org.apache.jasper.compiler.JDTCompiler"));
  }

  default SpanDataAssert assertRenderSpan(SpanDataAssert span, JspSpanAssertion spanAssertion) {
    String requestURL = spanAssertion.getRoute();
    if (spanAssertion.getRequestURLOverride() != null) {
      requestURL = spanAssertion.getRequestURLOverride();
    }

    if (spanAssertion.getExceptionClass() != null) {
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
                                      v ->
                                          val.isEqualTo(
                                              spanAssertion.getExceptionClass().getName()),
                                      v ->
                                          val.contains(
                                              spanAssertion.getExceptionClass().getSimpleName()))),
                          satisfies(
                              ExceptionAttributes.EXCEPTION_MESSAGE,
                              val -> val.isInstanceOf(String.class)),
                          satisfies(
                              ExceptionAttributes.EXCEPTION_STACKTRACE,
                              val -> val.isInstanceOf(String.class))));
    }

    return span.hasName("Render " + spanAssertion.getRoute())
        .hasParent(spanAssertion.getParent())
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("jsp.requestURL"), getBaseUrl() + requestURL),
            satisfies(
                stringKey("jsp.forwardOrigin"),
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(spanAssertion.getForwardOrigin()).isNull(),
                        v -> assertThat(v).isEqualTo(spanAssertion.getForwardOrigin()))));
  }

  class JspSpanAssertion {
    private SpanData parent;
    private String method;
    private String className;
    private String requestURLOverride;
    private String forwardOrigin;
    private String route;
    private int responseStatus;
    private Class<?> exceptionClass;

    public SpanData getParent() {
      return parent;
    }

    public void setParent(SpanData parent) {
      this.parent = parent;
    }

    public String getRequestURLOverride() {
      return requestURLOverride;
    }

    public void setRequestURLOverride(String requestURLOverride) {
      this.requestURLOverride = requestURLOverride;
    }

    public String getForwardOrigin() {
      return forwardOrigin;
    }

    public void setForwardOrigin(String forwardOrigin) {
      this.forwardOrigin = forwardOrigin;
    }

    public SpanData getSpanData() {
      return parent;
    }

    public void setSpanData(SpanData parent) {
      this.parent = parent;
    }

    public String getMethod() {
      return method;
    }

    public void setMethod(String method) {
      this.method = method;
    }

    public String getClassName() {
      return className;
    }

    public void setClassName(String className) {
      this.className = className;
    }

    public String getRoute() {
      return route;
    }

    public void setRoute(String route) {
      this.route = route;
    }

    public int getResponseStatus() {
      return responseStatus;
    }

    public void setResponseStatus(int responseStatus) {
      this.responseStatus = responseStatus;
    }

    public Class<?> getExceptionClass() {
      return exceptionClass;
    }

    public void setExceptionClass(Class<?> exceptionClass) {
      this.exceptionClass = exceptionClass;
    }
  }

  final class JspSpanAssertionBuilder {
    private SpanData parent;
    private String method;
    private String route;
    private String className;
    private String requestURLOverride;
    private String forwardOrigin;
    private int responseStatus;
    private Class<?> exceptionClass;

    public JspSpanAssertionBuilder withParent(SpanData parent) {
      this.parent = parent;
      return this;
    }

    public JspSpanAssertionBuilder withMethod(String method) {
      this.method = method;
      return this;
    }

    public JspSpanAssertionBuilder withRoute(String route) {
      this.route = route;
      return this;
    }

    public JspSpanAssertionBuilder withClassName(String className) {
      this.className = className;
      return this;
    }

    public JspSpanAssertionBuilder withRequestURLOverride(String requestURLOverride) {
      this.requestURLOverride = requestURLOverride;
      return this;
    }

    public JspSpanAssertionBuilder withForwardOrigin(String forwardOrigin) {
      this.forwardOrigin = forwardOrigin;
      return this;
    }

    public JspSpanAssertionBuilder withResponseStatus(int responseStatus) {
      this.responseStatus = responseStatus;
      return this;
    }

    public JspSpanAssertionBuilder withExceptionClass(Class<?> exceptionClass) {
      this.exceptionClass = exceptionClass;
      return this;
    }

    public JspSpanAssertion build() {
      JspSpanAssertion serverSpan = new JspSpanAssertion();
      serverSpan.setParent(this.parent);
      serverSpan.setMethod(this.method);
      serverSpan.setRoute(this.route);
      serverSpan.setClassName(this.className);
      serverSpan.setRequestURLOverride(this.requestURLOverride);
      serverSpan.setForwardOrigin(this.forwardOrigin);
      serverSpan.setResponseStatus(this.responseStatus);
      serverSpan.setExceptionClass(this.exceptionClass);
      return serverSpan;
    }
  }
}
