/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpclient;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.DefaultHttpClientTelemetryBuilder;
import io.opentelemetry.instrumentation.api.incubator.builder.HttpClientTelemetryBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.httpclient.internal.HttpHeadersSetter;
import io.opentelemetry.instrumentation.httpclient.internal.JavaHttpClientAttributesGetter;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class JavaHttpClientTelemetryBuilder
    implements HttpClientTelemetryBuilder<
        JavaHttpClientTelemetryBuilder, HttpRequest, HttpResponse<?>> {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.java-http-client";
  private final DefaultHttpClientTelemetryBuilder<HttpRequest, HttpResponse<?>> builder;

  JavaHttpClientTelemetryBuilder(OpenTelemetry openTelemetry) {
    builder =
        new DefaultHttpClientTelemetryBuilder<>(
            INSTRUMENTATION_NAME,
            openTelemetry,
            JavaHttpClientAttributesGetter.INSTANCE,
            Optional.empty());
  }

  @Override
  @CanIgnoreReturnValue
  public JavaHttpClientTelemetryBuilder addAttributeExtractor(
      AttributesExtractor<? super HttpRequest, ? super HttpResponse<?>> attributesExtractor) {
    builder.addAttributeExtractor(attributesExtractor);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public JavaHttpClientTelemetryBuilder setCapturedRequestHeaders(List<String> requestHeaders) {
    builder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public JavaHttpClientTelemetryBuilder setCapturedResponseHeaders(List<String> responseHeaders) {
    builder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public JavaHttpClientTelemetryBuilder setKnownMethods(Set<String> knownMethods) {
    builder.setKnownMethods(knownMethods);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public JavaHttpClientTelemetryBuilder setEmitExperimentalHttpClientMetrics(
      boolean emitExperimentalHttpClientMetrics) {
    builder.setEmitExperimentalHttpClientMetrics(emitExperimentalHttpClientMetrics);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public JavaHttpClientTelemetryBuilder setSpanNameExtractor(
      Function<SpanNameExtractor<HttpRequest>, ? extends SpanNameExtractor<? super HttpRequest>>
          spanNameExtractorTransformer) {
    builder.setSpanNameExtractor(spanNameExtractorTransformer);
    return this;
  }

  public JavaHttpClientTelemetry build() {
    return new JavaHttpClientTelemetry(
        builder.instrumenter(), new HttpHeadersSetter(builder.getOpenTelemetry().getPropagators()));
  }
}
