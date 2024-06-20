/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v5_2;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.DefaultHttpClientTelemetryBuilder;
import io.opentelemetry.instrumentation.api.incubator.builder.HttpClientTelemetryBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.apache.hc.core5.http.HttpResponse;

/** A builder for {@link ApacheHttpClient5Telemetry}. */
public final class ApacheHttpClient5TelemetryBuilder
    implements HttpClientTelemetryBuilder<
        ApacheHttpClient5TelemetryBuilder, ApacheHttpClient5Request, HttpResponse> {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-httpclient-5.2";
  private final DefaultHttpClientTelemetryBuilder<ApacheHttpClient5Request, HttpResponse> builder;

  ApacheHttpClient5TelemetryBuilder(OpenTelemetry openTelemetry) {
    builder =
        new DefaultHttpClientTelemetryBuilder<>(
            INSTRUMENTATION_NAME,
            openTelemetry,
            ApacheHttpClient5HttpAttributesGetter.INSTANCE,
            // We manually inject because we need to inject internal requests for redirects.
            Optional.empty());
  }

  @Override
  @CanIgnoreReturnValue
  public ApacheHttpClient5TelemetryBuilder addAttributeExtractor(
      AttributesExtractor<? super ApacheHttpClient5Request, ? super HttpResponse>
          attributesExtractor) {
    builder.addAttributeExtractor(attributesExtractor);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public ApacheHttpClient5TelemetryBuilder setCapturedRequestHeaders(List<String> requestHeaders) {
    builder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public ApacheHttpClient5TelemetryBuilder setCapturedResponseHeaders(
      List<String> responseHeaders) {
    builder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public ApacheHttpClient5TelemetryBuilder setKnownMethods(Set<String> knownMethods) {
    builder.setKnownMethods(knownMethods);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public ApacheHttpClient5TelemetryBuilder setEmitExperimentalHttpClientMetrics(
      boolean emitExperimentalHttpClientMetrics) {
    builder.setEmitExperimentalHttpClientMetrics(emitExperimentalHttpClientMetrics);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public ApacheHttpClient5TelemetryBuilder setSpanNameExtractor(
      Function<
              SpanNameExtractor<ApacheHttpClient5Request>,
              ? extends SpanNameExtractor<? super ApacheHttpClient5Request>>
          spanNameExtractorTransformer) {
    builder.setSpanNameExtractor(spanNameExtractorTransformer);
    return this;
  }

  /**
   * Returns a new {@link ApacheHttpClient5Telemetry} configured with this {@link
   * ApacheHttpClient5TelemetryBuilder}.
   */
  public ApacheHttpClient5Telemetry build() {
    return new ApacheHttpClient5Telemetry(
        builder.instrumenter(), builder.getOpenTelemetry().getPropagators());
  }
}
