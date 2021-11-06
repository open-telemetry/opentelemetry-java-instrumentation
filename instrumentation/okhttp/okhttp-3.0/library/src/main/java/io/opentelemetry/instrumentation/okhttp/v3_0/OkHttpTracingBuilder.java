/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import static io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor.alwaysClient;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.okhttp.v3_0.internal.OkHttpNetAttributesExtractor;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Request;
import okhttp3.Response;

/** A builder of {@link OkHttpTracing}. */
public final class OkHttpTracingBuilder {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.okhttp-3.0";

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<Request, Response>> additionalExtractors =
      new ArrayList<>();
  private CapturedHttpHeaders capturedHttpHeaders = CapturedHttpHeaders.client(Config.get());

  OkHttpTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  public OkHttpTracingBuilder addAttributesExtractor(
      AttributesExtractor<Request, Response> attributesExtractor) {
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
  public OkHttpTracingBuilder captureHttpHeaders(CapturedHttpHeaders capturedHttpHeaders) {
    this.capturedHttpHeaders = capturedHttpHeaders;
    return this;
  }

  /** Returns a new {@link OkHttpTracing} with the settings of this {@link OkHttpTracingBuilder}. */
  public OkHttpTracing build() {
    OkHttpAttributesExtractor httpAttributesExtractor =
        new OkHttpAttributesExtractor(capturedHttpHeaders);
    OkHttpNetAttributesExtractor netAttributesExtractor = new OkHttpNetAttributesExtractor();

    Instrumenter<Request, Response> instrumenter =
        Instrumenter.<Request, Response>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(httpAttributesExtractor))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesExtractor))
            .addAttributesExtractor(httpAttributesExtractor)
            .addAttributesExtractor(netAttributesExtractor)
            .addAttributesExtractors(additionalExtractors)
            .addRequestMetrics(HttpClientMetrics.get())
            .addSpanLinksExtractor(
                new SpanLinksExtractor<Request>() {
                  @Override
                  public void extract(
                      SpanLinksBuilder spanLinksBuilder, Context context, Request request) {

                    TracingInterceptor.TryInfo info =
                        TracingInterceptor.TryInfo.getTryInfo(context);
                    if (info.getSpanContext().isValid()) {
                      spanLinksBuilder.addLink(info.getSpanContext());
                    }
                  }
                })
            .newInstrumenter(alwaysClient());
    return new OkHttpTracing(instrumenter, openTelemetry.getPropagators());
  }
}
