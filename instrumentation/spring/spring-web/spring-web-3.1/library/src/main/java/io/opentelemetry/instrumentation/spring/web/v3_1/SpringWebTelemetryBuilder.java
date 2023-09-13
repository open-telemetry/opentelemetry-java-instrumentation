/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web.v3_1;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientExperimentalMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

/** A builder of {@link SpringWebTelemetry}. */
public final class SpringWebTelemetryBuilder {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-web-3.1";

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<HttpRequest, ClientHttpResponse>> additionalExtractors =
      new ArrayList<>();
  private final HttpClientAttributesExtractorBuilder<HttpRequest, ClientHttpResponse>
      httpAttributesExtractorBuilder =
          HttpClientAttributesExtractor.builder(SpringWebHttpAttributesGetter.INSTANCE);
  private final HttpSpanNameExtractorBuilder<HttpRequest> httpSpanNameExtractorBuilder =
      HttpSpanNameExtractor.builder(SpringWebHttpAttributesGetter.INSTANCE);
  private boolean emitExperimentalHttpClientMetrics = false;

  @Nullable
  private Function<SpanNameExtractor<HttpRequest>, ? extends SpanNameExtractor<? super HttpRequest>>
      spanNameExtractorTransformer;

  SpringWebTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  @CanIgnoreReturnValue
  public SpringWebTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<HttpRequest, ClientHttpResponse> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public SpringWebTelemetryBuilder setCapturedRequestHeaders(List<String> requestHeaders) {
    httpAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public SpringWebTelemetryBuilder setCapturedResponseHeaders(List<String> responseHeaders) {
    httpAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /** Sets custom {@link SpanNameExtractor} via transform function. */
  @CanIgnoreReturnValue
  public SpringWebTelemetryBuilder setSpanNameExtractor(
      Function<SpanNameExtractor<HttpRequest>, ? extends SpanNameExtractor<? super HttpRequest>>
          spanNameExtractor) {
    this.spanNameExtractorTransformer = spanNameExtractor;
    return this;
  }

  /**
   * Configures the instrumentation to recognize an alternative set of HTTP request methods.
   *
   * <p>By default, this instrumentation defines "known" methods as the ones listed in <a
   * href="https://www.rfc-editor.org/rfc/rfc9110.html#name-methods">RFC9110</a> and the PATCH
   * method defined in <a href="https://www.rfc-editor.org/rfc/rfc5789.html">RFC5789</a>.
   *
   * <p>Note: calling this method <b>overrides</b> the default known method sets completely; it does
   * not supplement it.
   *
   * @param knownMethods A set of recognized HTTP request methods.
   * @see HttpClientAttributesExtractorBuilder#setKnownMethods(Set)
   */
  @CanIgnoreReturnValue
  public SpringWebTelemetryBuilder setKnownMethods(Set<String> knownMethods) {
    httpAttributesExtractorBuilder.setKnownMethods(knownMethods);
    httpSpanNameExtractorBuilder.setKnownMethods(knownMethods);
    return this;
  }

  /**
   * Configures the instrumentation to emit experimental HTTP client metrics.
   *
   * @param emitExperimentalHttpClientMetrics {@code true} if the experimental HTTP client metrics
   *     are to be emitted.
   */
  @CanIgnoreReturnValue
  public SpringWebTelemetryBuilder setEmitExperimentalHttpClientMetrics(
      boolean emitExperimentalHttpClientMetrics) {
    this.emitExperimentalHttpClientMetrics = emitExperimentalHttpClientMetrics;
    return this;
  }

  /**
   * Returns a new {@link SpringWebTelemetry} with the settings of this {@link
   * SpringWebTelemetryBuilder}.
   */
  public SpringWebTelemetry build() {
    SpringWebHttpAttributesGetter httpAttributeGetter = SpringWebHttpAttributesGetter.INSTANCE;

    SpanNameExtractor<HttpRequest> originalSpanNameExtractor = httpSpanNameExtractorBuilder.build();
    SpanNameExtractor<? super HttpRequest> spanNameExtractor = originalSpanNameExtractor;
    if (spanNameExtractorTransformer != null) {
      spanNameExtractor = spanNameExtractorTransformer.apply(originalSpanNameExtractor);
    }

    InstrumenterBuilder<HttpRequest, ClientHttpResponse> builder =
        Instrumenter.<HttpRequest, ClientHttpResponse>builder(
                openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor)
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributeGetter))
            .addAttributesExtractor(httpAttributesExtractorBuilder.build())
            .addAttributesExtractors(additionalExtractors)
            .addOperationMetrics(HttpClientMetrics.get());
    if (emitExperimentalHttpClientMetrics) {
      builder.addOperationMetrics(HttpClientExperimentalMetrics.get());
    }

    Instrumenter<HttpRequest, ClientHttpResponse> instrumenter =
        builder.buildClientInstrumenter(HttpRequestSetter.INSTANCE);
    return new SpringWebTelemetry(instrumenter);
  }
}
