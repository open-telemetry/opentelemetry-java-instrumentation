/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.instrumentation.api.config.Config;

/** A builder of {@link HttpServerAttributesExtractor}. */
public final class HttpServerAttributesExtractorBuilder<REQUEST, RESPONSE> {

  final HttpServerAttributesGetter<REQUEST, RESPONSE> getter;
  CapturedHttpHeaders capturedHttpHeaders = CapturedHttpHeaders.server(Config.get());

  HttpServerAttributesExtractorBuilder(HttpServerAttributesGetter<REQUEST, RESPONSE> getter) {
    this.getter = getter;
  }

  /**
   * Configures the HTTP headers that will be captured as span attributes.
   *
   * @param capturedHttpHeaders A configuration object specifying which HTTP request and response
   *     headers should be captured as span attributes.
   */
  public HttpServerAttributesExtractorBuilder<REQUEST, RESPONSE> captureHttpHeaders(
      CapturedHttpHeaders capturedHttpHeaders) {
    this.capturedHttpHeaders = capturedHttpHeaders;
    return this;
  }

  /**
   * Returns a new {@link HttpServerAttributesExtractor} with the settings of this {@link
   * HttpServerAttributesExtractorBuilder}.
   */
  public HttpServerAttributesExtractor<REQUEST, RESPONSE> build() {
    return new HttpServerAttributesExtractor<>(getter, capturedHttpHeaders);
  }
}
