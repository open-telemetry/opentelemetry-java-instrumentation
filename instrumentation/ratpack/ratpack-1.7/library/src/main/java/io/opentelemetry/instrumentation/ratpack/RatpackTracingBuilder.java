/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;
import io.opentelemetry.instrumentation.ratpack.internal.RatpackHttpNetAttributesGetter;
import io.opentelemetry.instrumentation.ratpack.internal.RatpackNetAttributesGetter;
import java.util.ArrayList;
import java.util.List;
import ratpack.http.Request;
import ratpack.http.Response;
import ratpack.http.client.HttpResponse;
import ratpack.http.client.RequestSpec;

/** A builder for {@link RatpackTracing}. */
public final class RatpackTracingBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.ratpack-1.4";

  private final OpenTelemetry openTelemetry;

  private final List<AttributesExtractor<? super Request, ? super Response>> additionalExtractors =
      new ArrayList<>();
  private CapturedHttpHeaders capturedHttpHeaders = CapturedHttpHeaders.server(Config.get());

  private final List<AttributesExtractor<? super RequestSpec, ? super HttpResponse>>
      additionalHttpClientExtractors = new ArrayList<>();

  RatpackTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items. The {@link AttributesExtractor} will be executed after all default extractors.
   */
  public RatpackTracingBuilder addAttributeExtractor(
      AttributesExtractor<? super Request, ? super Response> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  public RatpackTracingBuilder addClientAttributeExtractor(
      AttributesExtractor<? super RequestSpec, ? super HttpResponse> attributesExtractor) {
    additionalHttpClientExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configure the instrumentation to capture chosen HTTP request and response headers as span
   * attributes.
   *
   * @param capturedHttpHeaders An instance of {@link CapturedHttpHeaders} containing the configured
   *     HTTP request and response names.
   */
  public RatpackTracingBuilder captureHttpHeaders(CapturedHttpHeaders capturedHttpHeaders) {
    this.capturedHttpHeaders = capturedHttpHeaders;
    return this;
  }

  /** Returns a new {@link RatpackTracing} with the configuration of this builder. */
  public RatpackTracing build() {
    RatpackNetAttributesGetter netAttributes = new RatpackNetAttributesGetter();
    RatpackHttpAttributesExtractor httpAttributes =
        new RatpackHttpAttributesExtractor(capturedHttpHeaders);

    Instrumenter<Request, Response> instrumenter =
        Instrumenter.<Request, Response>builder(
                openTelemetry, INSTRUMENTATION_NAME, HttpSpanNameExtractor.create(httpAttributes))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributes))
            .addAttributesExtractor(NetServerAttributesExtractor.create(netAttributes))
            .addAttributesExtractor(httpAttributes)
            .addAttributesExtractors(additionalExtractors)
            .addRequestMetrics(HttpServerMetrics.get())
            .newServerInstrumenter(RatpackGetter.INSTANCE);

    return new RatpackTracing(instrumenter, httpClientInstrumenter());
  }

  private Instrumenter<RequestSpec, HttpResponse> httpClientInstrumenter() {
    RatpackHttpNetAttributesGetter netAttributes = new RatpackHttpNetAttributesGetter();
    RatpackHttpClientAttributesExtractor httpAttributes =
        new RatpackHttpClientAttributesExtractor(capturedHttpHeaders);

    return Instrumenter.<RequestSpec, HttpResponse>builder(
            openTelemetry, INSTRUMENTATION_NAME, HttpSpanNameExtractor.create(httpAttributes))
        .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributes))
        .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributes))
        .addAttributesExtractor(httpAttributes)
        .addAttributesExtractors(additionalHttpClientExtractors)
        .addRequestMetrics(HttpServerMetrics.get())
        .newClientInstrumenter(RequestHeaderSetter.INSTANCE);
  }
}
