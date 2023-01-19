/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * These options are deprecated as we move away from AbstractHttpClientTest and over to
 * HttpClientTests, which uses immutable HttpClientTestOptions.
 */
// @Deprecated - migrate to HttpClientTests and HttpClientTestOptions
public final class LegacyHttpClientTestOptions {

  public static final Set<AttributeKey<?>> DEFAULT_HTTP_ATTRIBUTES =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList(
                  SemanticAttributes.NET_PEER_NAME,
                  SemanticAttributes.NET_PEER_PORT,
                  SemanticAttributes.HTTP_URL,
                  SemanticAttributes.HTTP_METHOD,
                  SemanticAttributes.HTTP_FLAVOR,
                  SemanticAttributes.HTTP_USER_AGENT)));

  public static final BiFunction<URI, String, String> DEFAULT_EXPECTED_CLIENT_SPAN_NAME_MAPPER =
      (uri, method) -> method != null ? "HTTP " + method : "HTTP request";

  Function<URI, Set<AttributeKey<?>>> httpAttributes = unused -> DEFAULT_HTTP_ATTRIBUTES;

  BiFunction<URI, String, String> expectedClientSpanNameMapper =
      DEFAULT_EXPECTED_CLIENT_SPAN_NAME_MAPPER;

  Integer responseCodeOnRedirectError = null;
  String userAgent = null;

  BiFunction<URI, Throwable, Throwable> clientSpanErrorMapper = (uri, exception) -> exception;

  BiFunction<String, Integer, SingleConnection> singleConnectionFactory = (host, port) -> null;

  boolean testWithClientParent = true;
  boolean testRedirects = true;
  boolean testCircularRedirects = true;
  int maxRedirects = 2;
  boolean testReusedRequest = true;
  boolean testConnectionFailure = true;
  boolean testReadTimeout = false;
  boolean testRemoteConnection = true;
  boolean testHttps = true;
  boolean testCallback = true;
  boolean testCallbackWithParent = true;
  boolean testCallbackWithImplicitParent = false;
  boolean testErrorWithCallback = true;

  LegacyHttpClientTestOptions() {}

  @CanIgnoreReturnValue
  public LegacyHttpClientTestOptions setHttpAttributes(
      Function<URI, Set<AttributeKey<?>>> httpAttributes) {
    this.httpAttributes = httpAttributes;
    return this;
  }

  @CanIgnoreReturnValue
  public LegacyHttpClientTestOptions setExpectedClientSpanNameMapper(
      BiFunction<URI, String, String> expectedClientSpanNameMapper) {
    this.expectedClientSpanNameMapper = expectedClientSpanNameMapper;
    return this;
  }

  @CanIgnoreReturnValue
  public LegacyHttpClientTestOptions setResponseCodeOnRedirectError(
      int responseCodeOnRedirectError) {
    this.responseCodeOnRedirectError = responseCodeOnRedirectError;
    return this;
  }

  @CanIgnoreReturnValue
  public LegacyHttpClientTestOptions setUserAgent(String userAgent) {
    this.userAgent = userAgent;
    return this;
  }

  @CanIgnoreReturnValue
  public LegacyHttpClientTestOptions setClientSpanErrorMapper(
      BiFunction<URI, Throwable, Throwable> clientSpanErrorMapper) {
    this.clientSpanErrorMapper = clientSpanErrorMapper;
    return this;
  }

  @CanIgnoreReturnValue
  public LegacyHttpClientTestOptions setSingleConnectionFactory(
      BiFunction<String, Integer, SingleConnection> singleConnectionFactory) {
    this.singleConnectionFactory = singleConnectionFactory;
    return this;
  }

  @CanIgnoreReturnValue
  public LegacyHttpClientTestOptions setMaxRedirects(int maxRedirects) {
    this.maxRedirects = maxRedirects;
    return this;
  }

  @CanIgnoreReturnValue
  public LegacyHttpClientTestOptions disableTestWithClientParent() {
    testWithClientParent = false;
    return this;
  }

  @CanIgnoreReturnValue
  public LegacyHttpClientTestOptions disableTestRedirects() {
    testRedirects = false;
    return this;
  }

  @CanIgnoreReturnValue
  public LegacyHttpClientTestOptions disableTestCircularRedirects() {
    testCircularRedirects = false;
    return this;
  }

  @CanIgnoreReturnValue
  public LegacyHttpClientTestOptions disableTestReusedRequest() {
    testReusedRequest = false;
    return this;
  }

  @CanIgnoreReturnValue
  public LegacyHttpClientTestOptions disableTestConnectionFailure() {
    testConnectionFailure = false;
    return this;
  }

  @CanIgnoreReturnValue
  public LegacyHttpClientTestOptions enableTestReadTimeout() {
    testReadTimeout = true;
    return this;
  }

  @CanIgnoreReturnValue
  public LegacyHttpClientTestOptions disableTestRemoteConnection() {
    testRemoteConnection = false;
    return this;
  }

  @CanIgnoreReturnValue
  public LegacyHttpClientTestOptions disableTestHttps() {
    testHttps = false;
    return this;
  }

  @CanIgnoreReturnValue
  public LegacyHttpClientTestOptions disableTestCallback() {
    testCallback = false;
    return this;
  }

  @CanIgnoreReturnValue
  public LegacyHttpClientTestOptions disableTestCallbackWithParent() {
    testCallbackWithParent = false;
    return this;
  }

  @CanIgnoreReturnValue
  public LegacyHttpClientTestOptions enableTestCallbackWithImplicitParent() {
    testCallbackWithImplicitParent = true;
    return this;
  }

  @CanIgnoreReturnValue
  public LegacyHttpClientTestOptions disableTestErrorWithCallback() {
    testErrorWithCallback = false;
    return this;
  }
}
