/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import io.opentelemetry.api.common.AttributeKey;
import java.net.URI;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class HttpClientTestOptions {

  public final Function<URI, Set<AttributeKey<?>>> httpAttributes;
  public final BiFunction<URI, String, String> expectedClientSpanNameMapper;
  public final Integer responseCodeOnRedirectError;
  public final String userAgent;

  public final BiFunction<URI, Throwable, Throwable> clientSpanErrorMapper =
      (uri, exception) -> exception;

  public final BiFunction<String, Integer, SingleConnection> singleConnectionFactory =
      (host, port) -> null;

  public final boolean testWithClientParent;
  public final boolean testRedirects;
  public final boolean testCircularRedirects;
  public final int maxRedirects;
  public final boolean testReusedRequest;
  public final boolean testConnectionFailure;
  public final boolean testReadTimeout;
  public final boolean testRemoteConnection;
  public final boolean testHttps;
  public final boolean testCallback;
  public final boolean testCallbackWithParent;
  public final boolean testCallbackWithImplicitParent;
  public final boolean testErrorWithCallback;

  HttpClientTestOptions(HttpClientTestOptionsBuilder builder) {
    this.httpAttributes = builder.httpAttributes;
    this.expectedClientSpanNameMapper = builder.expectedClientSpanNameMapper;
    this.responseCodeOnRedirectError = builder.responseCodeOnRedirectError;
    this.userAgent = builder.userAgent;
    this.testWithClientParent = builder.testWithClientParent;
    this.testRedirects = builder.testRedirects;
    this.testCircularRedirects = builder.testCircularRedirects;
    this.maxRedirects = builder.maxRedirects;
    this.testReusedRequest = builder.testReusedRequest;
    this.testConnectionFailure = builder.testConnectionFailure;
    this.testReadTimeout = builder.testReadTimeout;
    this.testRemoteConnection = builder.testRemoteConnection;
    this.testHttps = builder.testHttps;
    this.testCallback = builder.testCallback;
    this.testCallbackWithParent = builder.testCallbackWithParent;
    this.testCallbackWithImplicitParent = builder.testCallbackWithImplicitParent;
    this.testErrorWithCallback = builder.testErrorWithCallback;
  }

  public static HttpClientTestOptionsBuilder builder() {
    return new HttpClientTestOptionsBuilder();
  }
}
