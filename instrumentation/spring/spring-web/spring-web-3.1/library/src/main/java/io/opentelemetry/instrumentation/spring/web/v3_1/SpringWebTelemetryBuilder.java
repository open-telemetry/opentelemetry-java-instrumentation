/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web.v3_1;

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
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

/** A builder of {@link SpringWebTelemetry}. */
public final class SpringWebTelemetryBuilder
    implements HttpClientTelemetryBuilder<
        SpringWebTelemetryBuilder, HttpRequest, ClientHttpResponse> {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-web-3.1";
  private final DefaultHttpClientTelemetryBuilder<HttpRequest, ClientHttpResponse> builder;

  SpringWebTelemetryBuilder(OpenTelemetry openTelemetry) {
    builder =
        new DefaultHttpClientTelemetryBuilder<>(
            INSTRUMENTATION_NAME,
            openTelemetry,
            SpringWebHttpAttributesGetter.INSTANCE,
            Optional.of(HttpRequestSetter.INSTANCE));
  }

  @Override
  @CanIgnoreReturnValue
  public SpringWebTelemetryBuilder addAttributeExtractor(
      AttributesExtractor<? super HttpRequest, ? super ClientHttpResponse> attributesExtractor) {
    builder.addAttributeExtractor(attributesExtractor);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public SpringWebTelemetryBuilder setCapturedRequestHeaders(List<String> requestHeaders) {
    builder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public SpringWebTelemetryBuilder setCapturedResponseHeaders(List<String> responseHeaders) {
    builder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public SpringWebTelemetryBuilder setSpanNameExtractor(
      Function<SpanNameExtractor<HttpRequest>, ? extends SpanNameExtractor<? super HttpRequest>>
          spanNameExtractorTransformer) {
    builder.setSpanNameExtractor(spanNameExtractorTransformer);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public SpringWebTelemetryBuilder setKnownMethods(Set<String> knownMethods) {
    builder.setKnownMethods(knownMethods);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public SpringWebTelemetryBuilder setEmitExperimentalHttpClientMetrics(
      boolean emitExperimentalHttpClientMetrics) {
    builder.setEmitExperimentalHttpClientMetrics(emitExperimentalHttpClientMetrics);
    return this;
  }

  /**
   * Returns a new {@link SpringWebTelemetry} with the settings of this {@link
   * SpringWebTelemetryBuilder}.
   */
  public SpringWebTelemetry build() {
    return new SpringWebTelemetry(builder.instrumenter());
  }
}
