/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.instrumentation.api.config.Config;

/** A builder of {@link HttpClientAttributesExtractor}. */
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
   */
  public HttpClientAttributesExtractorBuilder<REQUEST, RESPONSE> captureHttpHeaders(
      CapturedHttpHeaders capturedHttpHeaders) {
    this.capturedHttpHeaders = capturedHttpHeaders;
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
