/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v4_3;

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
import org.apache.http.HttpResponse;

/** A builder for {@link ApacheHttpClientTelemetry}. */
public final class ApacheHttpClientTelemetryBuilder
    implements HttpClientTelemetryBuilder<
        ApacheHttpClientTelemetryBuilder, ApacheHttpClientRequest, HttpResponse> {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-httpclient-4.3";
  private final DefaultHttpClientTelemetryBuilder<ApacheHttpClientRequest, HttpResponse> builder;

  ApacheHttpClientTelemetryBuilder(OpenTelemetry openTelemetry) {
    builder =
        new DefaultHttpClientTelemetryBuilder<>(
            INSTRUMENTATION_NAME,
            openTelemetry,
            ApacheHttpClientHttpAttributesGetter.INSTANCE,
            Optional.empty());
  }

  @Override
  @CanIgnoreReturnValue
  public ApacheHttpClientTelemetryBuilder addAttributeExtractor(
      AttributesExtractor<? super ApacheHttpClientRequest, ? super HttpResponse>
          attributesExtractor) {
    builder.addAttributeExtractor(attributesExtractor);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public ApacheHttpClientTelemetryBuilder setCapturedRequestHeaders(List<String> requestHeaders) {
    builder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public ApacheHttpClientTelemetryBuilder setCapturedResponseHeaders(List<String> responseHeaders) {
    builder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public ApacheHttpClientTelemetryBuilder setKnownMethods(Set<String> knownMethods) {
    builder.setKnownMethods(knownMethods);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public ApacheHttpClientTelemetryBuilder setEmitExperimentalHttpClientMetrics(
      boolean emitExperimentalHttpClientMetrics) {
    builder.setEmitExperimentalHttpClientMetrics(emitExperimentalHttpClientMetrics);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public ApacheHttpClientTelemetryBuilder setSpanNameExtractor(
      Function<
              SpanNameExtractor<ApacheHttpClientRequest>,
              ? extends SpanNameExtractor<? super ApacheHttpClientRequest>>
          spanNameExtractorTransformer) {
    builder.setSpanNameExtractor(spanNameExtractorTransformer);
    return this;
  }

  /**
   * Returns a new {@link ApacheHttpClientTelemetry} configured with this {@link
   * ApacheHttpClientTelemetryBuilder}.
   */
  public ApacheHttpClientTelemetry build() {
    // We manually inject because we need to inject internal requests for redirects.
    return new ApacheHttpClientTelemetry(
        builder.instrumenter(), builder.getOpenTelemetry().getPropagators());
  }
}
