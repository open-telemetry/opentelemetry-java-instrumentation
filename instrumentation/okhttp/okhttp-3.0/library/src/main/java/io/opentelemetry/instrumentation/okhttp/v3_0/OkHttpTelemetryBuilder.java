/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.DefaultHttpClientTelemetryBuilder;
import io.opentelemetry.instrumentation.api.incubator.builder.HttpClientTelemetryBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.okhttp.v3_0.internal.OkHttpAttributesGetter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import okhttp3.Request;
import okhttp3.Response;

/** A builder of {@link OkHttpTelemetry}. */
public final class OkHttpTelemetryBuilder
    implements HttpClientTelemetryBuilder<OkHttpTelemetryBuilder, Request, Response> {

  public static final String INSTRUMENTATION_NAME = "io.opentelemetry.okhttp-3.0";
  private final DefaultHttpClientTelemetryBuilder<Request, Response> builder;

  OkHttpTelemetryBuilder(OpenTelemetry openTelemetry) {
    builder =
        new DefaultHttpClientTelemetryBuilder<>(
            INSTRUMENTATION_NAME, openTelemetry, OkHttpAttributesGetter.INSTANCE, Optional.empty());
  }

  @Override
  @CanIgnoreReturnValue
  public OkHttpTelemetryBuilder addAttributeExtractor(
      AttributesExtractor<? super Request, ? super Response> attributesExtractor) {
    builder.addAttributeExtractor(attributesExtractor);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public OkHttpTelemetryBuilder setCapturedRequestHeaders(List<String> requestHeaders) {
    builder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public OkHttpTelemetryBuilder setCapturedResponseHeaders(List<String> responseHeaders) {
    builder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public OkHttpTelemetryBuilder setKnownMethods(Set<String> knownMethods) {
    builder.setKnownMethods(knownMethods);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public OkHttpTelemetryBuilder setEmitExperimentalHttpClientMetrics(
      boolean emitExperimentalHttpClientMetrics) {
    builder.setEmitExperimentalHttpClientMetrics(emitExperimentalHttpClientMetrics);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public OkHttpTelemetryBuilder setSpanNameExtractor(
      Function<SpanNameExtractor<Request>, ? extends SpanNameExtractor<? super Request>>
          spanNameExtractorTransformer) {
    builder.setSpanNameExtractor(spanNameExtractorTransformer);
    return this;
  }

  /**
   * Returns a new {@link OkHttpTelemetry} with the settings of this {@link OkHttpTelemetryBuilder}.
   */
  public OkHttpTelemetry build() {
    return new OkHttpTelemetry(builder.instrumenter(), builder.getOpenTelemetry().getPropagators());
  }
}
