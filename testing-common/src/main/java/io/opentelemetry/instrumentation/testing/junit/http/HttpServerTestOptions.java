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
import java.util.function.Predicate;

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

  Predicate<ServerEndpoint> hasHandlerSpan = unused -> false;
  Predicate<ServerEndpoint> hasResponseSpan = unused -> false;
  Predicate<ServerEndpoint> hasErrorPageSpans = unused -> false;

  Predicate<ServerEndpoint> hasExceptionOnServerSpan = endpoint -> !hasHandlerSpan.test(endpoint);

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

  public HttpServerTestOptions setHasHandlerSpan(Predicate<ServerEndpoint> hasHandlerSpan) {
    this.hasHandlerSpan = hasHandlerSpan;
    return this;
  }

  public HttpServerTestOptions setHasResponseSpan(Predicate<ServerEndpoint> hasResponseSpan) {
    this.hasResponseSpan = hasResponseSpan;
    return this;
  }

  public HttpServerTestOptions setHasErrorPageSpans(Predicate<ServerEndpoint> hasErrorPageSpans) {
    this.hasErrorPageSpans = hasErrorPageSpans;
    return this;
  }

  public HttpServerTestOptions setHasExceptionOnServerSpan(
      Predicate<ServerEndpoint> hasExceptionOnServerSpan) {
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
