/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public final class HttpServerTestOptions {

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  public static final Set<AttributeKey<?>> DEFAULT_HTTP_ATTRIBUTES =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList(
                  SemanticAttributes.HTTP_ROUTE,
                  SemconvStabilityUtil.getAttributeKey(SemanticAttributes.NET_PEER_PORT))));

  public static final SpanNameMapper DEFAULT_EXPECTED_SERVER_SPAN_NAME_MAPPER =
      (uri, method, route) -> {
        if (HttpConstants._OTHER.equals(method)) {
          method = "HTTP";
        }
        return route == null ? method : method + " " + route;
      };

  Function<ServerEndpoint, Set<AttributeKey<?>>> httpAttributes = unused -> DEFAULT_HTTP_ATTRIBUTES;
  SpanNameMapper expectedServerSpanNameMapper = DEFAULT_EXPECTED_SERVER_SPAN_NAME_MAPPER;
  BiFunction<ServerEndpoint, String, String> expectedHttpRoute = (endpoint, method) -> null;
  Function<ServerEndpoint, String> sockPeerAddr = unused -> "127.0.0.1";
  String contextPath = "";
  Throwable expectedException = new Exception(EXCEPTION.body);
  Supplier<String> metricsInstrumentationName = () -> null;
  // we're calling /success in the test, and most servers respond with 200 anyway
  int responseCodeOnNonStandardHttpMethod = ServerEndpoint.SUCCESS.status;

  Predicate<ServerEndpoint> hasHandlerSpan = unused -> false;
  Predicate<ServerEndpoint> hasResponseSpan = unused -> false;
  Predicate<ServerEndpoint> hasErrorPageSpans = unused -> false;
  Predicate<ServerEndpoint> hasResponseCustomizer = unused -> false;

  Predicate<ServerEndpoint> hasExceptionOnServerSpan = endpoint -> !hasHandlerSpan.test(endpoint);

  boolean testRedirect = true;
  boolean testError = true;
  boolean testErrorBody = true;
  boolean testException = true;
  boolean testNotFound = true;
  boolean testPathParam = false;
  boolean testCaptureHttpHeaders = true;
  boolean testCaptureRequestParameters = false;
  boolean testHttpPipelining = true;
  boolean testNonStandardHttpMethod = true;
  boolean verifyServerSpanEndTime = true;

  HttpServerTestOptions() {}

  @CanIgnoreReturnValue
  public HttpServerTestOptions setHttpAttributes(
      Function<ServerEndpoint, Set<AttributeKey<?>>> httpAttributes) {
    this.httpAttributes = httpAttributes;
    return this;
  }

  @CanIgnoreReturnValue
  public HttpServerTestOptions setExpectedServerSpanNameMapper(
      SpanNameMapper expectedServerSpanNameMapper) {
    this.expectedServerSpanNameMapper = expectedServerSpanNameMapper;
    return this;
  }

  @CanIgnoreReturnValue
  public HttpServerTestOptions setExpectedHttpRoute(
      BiFunction<ServerEndpoint, String, String> expectedHttpRoute) {
    this.expectedHttpRoute = expectedHttpRoute;
    return this;
  }

  @CanIgnoreReturnValue
  public HttpServerTestOptions setSockPeerAddr(Function<ServerEndpoint, String> sockPeerAddr) {
    this.sockPeerAddr = sockPeerAddr;
    return this;
  }

  @CanIgnoreReturnValue
  public HttpServerTestOptions setContextPath(String contextPath) {
    this.contextPath = contextPath;
    return this;
  }

  @CanIgnoreReturnValue
  public HttpServerTestOptions setExpectedException(Throwable expectedException) {
    this.expectedException = expectedException;
    return this;
  }

  @CanIgnoreReturnValue
  public HttpServerTestOptions setMetricsInstrumentationName(
      Supplier<String> metricsInstrumentationName) {
    this.metricsInstrumentationName = metricsInstrumentationName;
    return this;
  }

  @CanIgnoreReturnValue
  public HttpServerTestOptions setResponseCodeOnNonStandardHttpMethod(
      int responseCodeOnNonStandardHttpMethod) {
    this.responseCodeOnNonStandardHttpMethod = responseCodeOnNonStandardHttpMethod;
    return this;
  }

  @CanIgnoreReturnValue
  public HttpServerTestOptions setHasHandlerSpan(Predicate<ServerEndpoint> hasHandlerSpan) {
    this.hasHandlerSpan = hasHandlerSpan;
    return this;
  }

  @CanIgnoreReturnValue
  public HttpServerTestOptions setHasResponseSpan(Predicate<ServerEndpoint> hasResponseSpan) {
    this.hasResponseSpan = hasResponseSpan;
    return this;
  }

  @CanIgnoreReturnValue
  public HttpServerTestOptions setHasErrorPageSpans(Predicate<ServerEndpoint> hasErrorPageSpans) {
    this.hasErrorPageSpans = hasErrorPageSpans;
    return this;
  }

  @CanIgnoreReturnValue
  public HttpServerTestOptions setHasExceptionOnServerSpan(
      Predicate<ServerEndpoint> hasExceptionOnServerSpan) {
    this.hasExceptionOnServerSpan = hasExceptionOnServerSpan;
    return this;
  }

  @CanIgnoreReturnValue
  public HttpServerTestOptions setHasResponseCustomizer(
      Predicate<ServerEndpoint> hasResponseCustomizer) {
    this.hasResponseCustomizer = hasResponseCustomizer;
    return this;
  }

  @CanIgnoreReturnValue
  public HttpServerTestOptions setTestRedirect(boolean testRedirect) {
    this.testRedirect = testRedirect;
    return this;
  }

  @CanIgnoreReturnValue
  public HttpServerTestOptions setTestError(boolean testError) {
    this.testError = testError;
    return this;
  }

  @CanIgnoreReturnValue
  public HttpServerTestOptions setTestErrorBody(boolean testErrorBody) {
    this.testErrorBody = testErrorBody;
    return this;
  }

  @CanIgnoreReturnValue
  public HttpServerTestOptions setTestException(boolean testException) {
    this.testException = testException;
    return this;
  }

  @CanIgnoreReturnValue
  public HttpServerTestOptions setTestNotFound(boolean testNotFound) {
    this.testNotFound = testNotFound;
    return this;
  }

  @CanIgnoreReturnValue
  public HttpServerTestOptions setTestPathParam(boolean testPathParam) {
    this.testPathParam = testPathParam;
    return this;
  }

  @CanIgnoreReturnValue
  public HttpServerTestOptions setTestCaptureHttpHeaders(boolean testCaptureHttpHeaders) {
    this.testCaptureHttpHeaders = testCaptureHttpHeaders;
    return this;
  }

  @CanIgnoreReturnValue
  public HttpServerTestOptions setTestCaptureRequestParameters(
      boolean testCaptureRequestParameters) {
    this.testCaptureRequestParameters = testCaptureRequestParameters;
    return this;
  }

  @CanIgnoreReturnValue
  public HttpServerTestOptions setTestHttpPipelining(boolean testHttpPipelining) {
    this.testHttpPipelining = testHttpPipelining;
    return this;
  }

  // TODO: convert make this class follow the same pattern as HttpClientTestOptions
  @CanIgnoreReturnValue
  public HttpServerTestOptions disableTestNonStandardHttpMethod() {
    this.testNonStandardHttpMethod = false;
    return this;
  }

  @CanIgnoreReturnValue
  public HttpServerTestOptions setVerifyServerSpanEndTime(boolean verifyServerSpanEndTime) {
    this.verifyServerSpanEndTime = verifyServerSpanEndTime;
    return this;
  }

  @FunctionalInterface
  public interface SpanNameMapper {

    String apply(ServerEndpoint endpoint, String method, @Nullable String route);
  }
}
