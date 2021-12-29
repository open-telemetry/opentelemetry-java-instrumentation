/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.client;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.ratpack.client.internal.RatpackHttpNetAttributesExtractor;
import java.util.ArrayList;
import java.util.List;
import ratpack.http.client.HttpResponse;
import ratpack.http.client.RequestSpec;

/** A builder for {@link RatpackHttpTracing}. */
public final class RatpackHttpTracingBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.ratpack-1.4";

  private final OpenTelemetry openTelemetry;

  private final List<AttributesExtractor<? super RequestSpec, ? super HttpResponse>>
      additionalExtractors = new ArrayList<>();
  private CapturedHttpHeaders capturedHttpHeaders = CapturedHttpHeaders.client(Config.get());

  RatpackHttpTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items. The {@link AttributesExtractor} will be executed after all default extractors.
   */
  public RatpackHttpTracingBuilder addAttributeExtractor(
      AttributesExtractor<? super RequestSpec, ? super HttpResponse> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configure the instrumentation to capture chosen HTTP request and response headers as span
   * attributes.
   *
   * @param capturedHttpHeaders An instance of {@link CapturedHttpHeaders} containing the configured
   *     HTTP request and response names.
   */
  public RatpackHttpTracingBuilder captureHttpHeaders(CapturedHttpHeaders capturedHttpHeaders) {
    this.capturedHttpHeaders = capturedHttpHeaders;
    return this;
  }

  /** Returns a new {@link RatpackHttpTracing} with the configuration of this builder. */
  public RatpackHttpTracing build() {
    RatpackHttpNetAttributesExtractor netAttributes = new RatpackHttpNetAttributesExtractor();
    RatpackHttpClientAttributesExtractor httpAttributes =
        new RatpackHttpClientAttributesExtractor(capturedHttpHeaders);

    Instrumenter<RequestSpec, HttpResponse> instrumenter =
        Instrumenter.<RequestSpec, HttpResponse>builder(
                openTelemetry, INSTRUMENTATION_NAME, HttpSpanNameExtractor.create(httpAttributes))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributes))
            .addAttributesExtractor(netAttributes)
            .addAttributesExtractor(httpAttributes)
            .addAttributesExtractors(additionalExtractors)
            .addRequestMetrics(HttpServerMetrics.get())
            .newClientInstrumenter(RequestHeaderSetter.INSTANCE);

    return new RatpackHttpTracing(instrumenter);
  }
}
