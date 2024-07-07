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


class AbstractJspInstrumentationTest {

  static SpanDataAssert assertServerSpan(
      SpanDataAssert span,
      ServerSpanAssertion serverSpanAssertion) {
    if (serverSpanAssertion.getExceptionClass() != null) {
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
                                      v -> val.isEqualTo(serverSpanAssertion.getExceptionClass().getName()),
                                      v -> val.contains(serverSpanAssertion.getExceptionClass().getSimpleName()))),
                          satisfies(
                              ExceptionAttributes.EXCEPTION_MESSAGE,
                              val -> val.isInstanceOf(String.class)),
                          satisfies(
                              ExceptionAttributes.EXCEPTION_STACKTRACE,
                              val -> val.isInstanceOf(String.class))));
    }

    return span.hasName(serverSpanAssertion.getMethod() + " " + serverSpanAssertion.getRoute())
        .hasNoParent()
        .hasKind(SpanKind.SERVER)
        .hasAttributesSatisfyingExactly(
            equalTo(UrlAttributes.URL_SCHEME, "http"),
            equalTo(UrlAttributes.URL_PATH, serverSpanAssertion.getRoute()),
            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, serverSpanAssertion.getMethod()),
            equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, serverSpanAssertion.getResponseStatus()),
            satisfies(
                UserAgentAttributes.USER_AGENT_ORIGINAL, val -> val.isInstanceOf(String.class)),
            equalTo(HttpAttributes.HTTP_ROUTE, serverSpanAssertion.getRoute()),
            equalTo(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "1.1"),
            equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
            equalTo(ServerAttributes.SERVER_PORT, serverSpanAssertion.getPort()),
            equalTo(ClientAttributes.CLIENT_ADDRESS, "127.0.0.1"),
            equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"),
            satisfies(NetworkAttributes.NETWORK_PEER_PORT, val -> val.isInstanceOf(Long.class)),
            satisfies(
                ErrorAttributes.ERROR_TYPE,
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(serverSpanAssertion.getExceptionClass()).isNull(),
                        v -> assertThat(v).isEqualTo("500"))));
  }

  static final class ServerSpanAssertionBuilder {
    private SpanDataAssert span;
    private String method;
    private String route;
    private int port;
    private int responseStatus;
    private Class<?> exceptionClass;

    public ServerSpanAssertionBuilder withSpan(SpanDataAssert span) {
      this.span = span;
      return this;
    }

    public ServerSpanAssertionBuilder withMethod(String method) {
      this.method = method;
      return this;
    }

    public ServerSpanAssertionBuilder withRoute(String route) {
      this.route = route;
      return this;
    }

    public ServerSpanAssertionBuilder withPort(int port) {
      this.port = port;
      return this;
    }

    public ServerSpanAssertionBuilder withResponseStatus(int responseStatus) {
      this.responseStatus = responseStatus;
      return this;
    }

    public ServerSpanAssertionBuilder withExceptionClass(Class<?> exceptionClass) {
      this.exceptionClass = exceptionClass;
      return this;
    }

    public ServerSpanAssertion build() {
      ServerSpanAssertion serverSpan = new ServerSpanAssertion();
      serverSpan.setSpan(this.span);
      serverSpan.setMethod(this.method);
      serverSpan.setRoute(this.route);
      serverSpan.setPort(this.port);
      serverSpan.setResponseStatus(this.responseStatus);
      serverSpan.setExceptionClass(this.exceptionClass);
      return serverSpan;
    }
  }

  public static class ServerSpanAssertion {
    private SpanDataAssert span;
    private String method;
    private String route;
    private int port;
    private int responseStatus;
    private Class<?> exceptionClass;

    public SpanDataAssert getSpan() {
      return span;
    }

    public void setSpan(SpanDataAssert span) {
      this.span = span;
    }

    public String getMethod() {
      return method;
    }

    public void setMethod(String method) {
      this.method = method;
    }

    public String getRoute() {
      return route;
    }

    public void setRoute(String route) {
      this.route = route;
    }

    public int getPort() {
      return port;
    }

    public void setPort(int port) {
      this.port = port;
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

  SpanDataAssert assertCompileSpan(
      SpanDataAssert span,
      SpanData parent,
      String route,
      String className,
      Class<?> exceptionClass) {

    if (exceptionClass != null) {
      span.hasStatus(StatusData.error())
          .hasEventsSatisfyingExactly(
              event ->
                  event
                      .hasName("exception")
                      .hasAttributesSatisfyingExactly(
                          equalTo(
                              ExceptionAttributes.EXCEPTION_TYPE,
                              exceptionClass.getCanonicalName()),
                          satisfies(
                              ExceptionAttributes.EXCEPTION_STACKTRACE,
                              val -> val.isInstanceOf(String.class)),
                          satisfies(
                              ExceptionAttributes.EXCEPTION_MESSAGE,
                              val -> val.isInstanceOf(String.class))));
    }

    return span.hasName("Compile " + route)
        .hasParent(parent)
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("jsp.classFQCN"), "org.apache.jsp." + className),
            equalTo(stringKey("jsp.compiler"), "org.apache.jasper.compiler.JDTCompiler"));
  }

  SpanDataAssert assertRenderSpan(
      SpanDataAssert span,
      SpanData parent,
      String route,
      String requestURLOverride,
      String forwardOrigin,
      Class<?> exceptionClass) {
    String requestURL = route;
    if (requestURLOverride != null) {
      requestURL = requestURLOverride;
    }

    if (exceptionClass != null) {
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
                                      v -> val.isEqualTo(exceptionClass.getName()),
                                      v -> val.contains(exceptionClass.getSimpleName()))),
                          satisfies(
                              ExceptionAttributes.EXCEPTION_MESSAGE,
                              val -> val.isInstanceOf(String.class)),
                          satisfies(
                              ExceptionAttributes.EXCEPTION_STACKTRACE,
                              val -> val.isInstanceOf(String.class))));
    }

    return span.hasName("Render " + route)
        .hasParent(parent)
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("jsp.requestURL"), baseUrl + requestURL),
            satisfies(
                stringKey("jsp.forwardOrigin"),
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(forwardOrigin).isNull(),
                        v -> assertThat(v).isEqualTo(forwardOrigin))));
  }
}
