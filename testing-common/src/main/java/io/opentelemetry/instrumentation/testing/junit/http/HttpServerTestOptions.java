/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class HttpServerTestOptions {

  public static final Set<AttributeKey<?>> DEFAULT_HTTP_ATTRIBUTES =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList(
                  SemanticAttributes.HTTP_ROUTE,
                  SemanticAttributes.NET_TRANSPORT,
                  SemanticAttributes.NET_PEER_PORT)));

  public static final BiFunction<ServerEndpoint, String, String>
      DEFAULT_EXPECTED_SERVER_SPAN_NAME_MAPPER = (uri, method) -> "HTTP " + method;

  Function<ServerEndpoint, Set<AttributeKey<?>>> httpAttributes = unused -> DEFAULT_HTTP_ATTRIBUTES;
  BiFunction<ServerEndpoint, String, String> expectedServerSpanNameMapper =
      DEFAULT_EXPECTED_SERVER_SPAN_NAME_MAPPER;
  Function<ServerEndpoint, String> expectedHttpRoute = unused -> null;
  Function<ServerEndpoint, String> peerIp = unused -> "127.0.0.1";
  String contextPath = "";
  Class<? extends Throwable> expectedExceptionClass = Exception.class;

  Function<ServerEndpoint, Boolean> hasHandlerSpan = unused -> false;
  Function<ServerEndpoint, Boolean> hasResponseSpan = unused -> false;
  Function<ServerEndpoint, Boolean> hasErrorPageSpans = unused -> false;

  Function<ServerEndpoint, Boolean> hasExceptionOnServerSpan =
      endpoint -> !hasHandlerSpan.apply(endpoint);

  boolean testRedirect = true;
  boolean testError = true;
  boolean testErrorBody = true;
  boolean testException = true;
  boolean testNotFound = true;
  boolean testPathParam = false;
  boolean testCaptureHttpHeaders = true;
  boolean testCaptureRequestParameters = false;

  HttpServerTestOptions() {}

  public HttpServerTestOptions setHttpAttributes(
      Function<ServerEndpoint, Set<AttributeKey<?>>> httpAttributes) {
    this.httpAttributes = httpAttributes;
    return this;
  }

  public HttpServerTestOptions setExpectedServerSpanNameMapper(
      BiFunction<ServerEndpoint, String, String> expectedServerSpanNameMapper) {
    this.expectedServerSpanNameMapper = expectedServerSpanNameMapper;
    return this;
  }

  public HttpServerTestOptions setExpectedHttpRoute(
      Function<ServerEndpoint, String> expectedHttpRoute) {
    this.expectedHttpRoute = expectedHttpRoute;
    return this;
  }

  public HttpServerTestOptions setPeerIp(Function<ServerEndpoint, String> peerIp) {
    this.peerIp = peerIp;
    return this;
  }

  public HttpServerTestOptions setContextPath(String contextPath) {
    this.contextPath = contextPath;
    return this;
  }

  public HttpServerTestOptions setExpectedExceptionClass(
      Class<? extends Throwable> expectedExceptionClass) {
    this.expectedExceptionClass = expectedExceptionClass;
    return this;
  }

  public HttpServerTestOptions setHasHandlerSpan(Function<ServerEndpoint, Boolean> hasHandlerSpan) {
    this.hasHandlerSpan = hasHandlerSpan;
    return this;
  }

  public HttpServerTestOptions setHasResponseSpan(
      Function<ServerEndpoint, Boolean> hasResponseSpan) {
    this.hasResponseSpan = hasResponseSpan;
    return this;
  }

  public HttpServerTestOptions setHasErrorPageSpans(
      Function<ServerEndpoint, Boolean> hasErrorPageSpans) {
    this.hasErrorPageSpans = hasErrorPageSpans;
    return this;
  }

  public HttpServerTestOptions setHasExceptionOnServerSpan(
      Function<ServerEndpoint, Boolean> hasExceptionOnServerSpan) {
    this.hasExceptionOnServerSpan = hasExceptionOnServerSpan;
    return this;
  }

  public HttpServerTestOptions setTestRedirect(boolean testRedirect) {
    this.testRedirect = testRedirect;
    return this;
  }

  public HttpServerTestOptions setTestError(boolean testError) {
    this.testError = testError;
    return this;
  }

  public HttpServerTestOptions setTestErrorBody(boolean testErrorBody) {
    this.testErrorBody = testErrorBody;
    return this;
  }

  public HttpServerTestOptions setTestException(boolean testException) {
    this.testException = testException;
    return this;
  }

  public HttpServerTestOptions setTestNotFound(boolean testNotFound) {
    this.testNotFound = testNotFound;
    return this;
  }

  public HttpServerTestOptions setTestPathParam(boolean testPathParam) {
    this.testPathParam = testPathParam;
    return this;
  }

  public HttpServerTestOptions setTestCaptureHttpHeaders(boolean testCaptureHttpHeaders) {
    this.testCaptureHttpHeaders = testCaptureHttpHeaders;
    return this;
  }

  public HttpServerTestOptions setTestCaptureRequestParameters(
      boolean testCaptureRequestParameters) {
    this.testCaptureRequestParameters = testCaptureRequestParameters;
    return this;
  }
}
