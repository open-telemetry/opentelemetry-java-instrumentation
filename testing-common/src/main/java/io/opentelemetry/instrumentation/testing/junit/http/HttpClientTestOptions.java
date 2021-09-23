/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class HttpClientTestOptions {

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
  boolean testCausality = true;
  boolean testCausalityWithCallback = true;
  boolean testCallback = true;
  boolean testCallbackWithParent = true;
  boolean testErrorWithCallback = true;

  HttpClientTestOptions() {}

  public HttpClientTestOptions setHttpAttributes(
      Function<URI, Set<AttributeKey<?>>> httpAttributes) {
    this.httpAttributes = httpAttributes;
    return this;
  }

  public HttpClientTestOptions setExpectedClientSpanNameMapper(
      BiFunction<URI, String, String> expectedClientSpanNameMapper) {
    this.expectedClientSpanNameMapper = expectedClientSpanNameMapper;
    return this;
  }

  public HttpClientTestOptions setResponseCodeOnRedirectError(int responseCodeOnRedirectError) {
    this.responseCodeOnRedirectError = responseCodeOnRedirectError;
    return this;
  }

  public HttpClientTestOptions setUserAgent(String userAgent) {
    this.userAgent = userAgent;
    return this;
  }

  public HttpClientTestOptions setClientSpanErrorMapper(
      BiFunction<URI, Throwable, Throwable> clientSpanErrorMapper) {
    this.clientSpanErrorMapper = clientSpanErrorMapper;
    return this;
  }

  public HttpClientTestOptions setSingleConnectionFactory(
      BiFunction<String, Integer, SingleConnection> singleConnectionFactory) {
    this.singleConnectionFactory = singleConnectionFactory;
    return this;
  }

  public HttpClientTestOptions setMaxRedirects(int maxRedirects) {
    this.maxRedirects = maxRedirects;
    return this;
  }

  public HttpClientTestOptions disableTestWithClientParent() {
    testWithClientParent = false;
    return this;
  }

  public HttpClientTestOptions disableTestRedirects() {
    testRedirects = false;
    return this;
  }

  public HttpClientTestOptions disableTestCircularRedirects() {
    testCircularRedirects = false;
    return this;
  }

  public HttpClientTestOptions disableTestReusedRequest() {
    testReusedRequest = false;
    return this;
  }

  public HttpClientTestOptions disableTestConnectionFailure() {
    testConnectionFailure = false;
    return this;
  }

  public HttpClientTestOptions enableTestReadTimeout() {
    testReadTimeout = true;
    return this;
  }

  public HttpClientTestOptions disableTestRemoteConnection() {
    testRemoteConnection = false;
    return this;
  }

  public HttpClientTestOptions disableTestHttps() {
    testHttps = false;
    return this;
  }

  public HttpClientTestOptions disableTestCausality() {
    testCausality = false;
    return this;
  }

  public HttpClientTestOptions disableTestCausalityWithCallback() {
    testCausalityWithCallback = false;
    return this;
  }

  public HttpClientTestOptions disableTestCallback() {
    testCallback = false;
    return this;
  }

  public HttpClientTestOptions disableTestCallbackWithParent() {
    testCallbackWithParent = false;
    return this;
  }

  public HttpClientTestOptions disableTestErrorWithCallback() {
    testErrorWithCallback = false;
    return this;
  }
}
