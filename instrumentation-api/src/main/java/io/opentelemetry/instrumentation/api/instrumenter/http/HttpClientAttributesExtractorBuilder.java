/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.instrumentation.api.config.Config;
import java.util.List;

/** A builder of {@link HttpClientAttributesExtractor}. */
@SuppressWarnings("deprecation") // suppress CapturedHttpHeaders deprecation
public final class HttpClientAttributesExtractorBuilder<REQUEST, RESPONSE> {

  final HttpClientAttributesGetter<REQUEST, RESPONSE> getter;
  CapturedHttpHeaders capturedHttpHeaders = CapturedHttpHeaders.client(Config.get());

  HttpClientAttributesExtractorBuilder(HttpClientAttributesGetter<REQUEST, RESPONSE> getter) {
    this.getter = getter;
  }

  /**
   * Configures the HTTP headers that will be captured as span attributes.
   *
   * @param capturedHttpHeaders A configuration object specifying which HTTP request and response
   *     headers should be captured as span attributes.
   * @deprecated Use {@link #setCapturedRequestHeaders(List)} and {@link
   *     #setCapturedResponseHeaders(List)} instead.
   */
  @Deprecated
  public HttpClientAttributesExtractorBuilder<REQUEST, RESPONSE> captureHttpHeaders(
      CapturedHttpHeaders capturedHttpHeaders) {
    this.capturedHttpHeaders = capturedHttpHeaders;
    return this;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  public HttpClientAttributesExtractorBuilder<REQUEST, RESPONSE> setCapturedRequestHeaders(
      List<String> requestHeaders) {
    this.capturedHttpHeaders =
        CapturedHttpHeaders.create(requestHeaders, capturedHttpHeaders.responseHeaders());
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  public HttpClientAttributesExtractorBuilder<REQUEST, RESPONSE> setCapturedResponseHeaders(
      List<String> responseHeaders) {
    this.capturedHttpHeaders =
        CapturedHttpHeaders.create(capturedHttpHeaders.requestHeaders(), responseHeaders);
    return this;
  }

  /**
   * Returns a new {@link HttpClientAttributesExtractor} with the settings of this {@link
   * HttpClientAttributesExtractorBuilder}.
   */
  public HttpClientAttributesExtractor<REQUEST, RESPONSE> build() {
    return new HttpClientAttributesExtractor<>(getter, capturedHttpHeaders);
  }
}
