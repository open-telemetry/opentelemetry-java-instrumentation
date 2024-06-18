/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.builder;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpClientExperimentalMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpClientPeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpExperimentalAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceResolver;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanStatusExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public abstract class HttpClientConfigBuilder<SELF, REQUEST, RESPONSE> {

  protected final OpenTelemetry openTelemetry;

  private final List<AttributesExtractor<? super REQUEST, ? super RESPONSE>> additionalExtractors =
      new ArrayList<>();
  private final HttpClientAttributesExtractorBuilder<REQUEST, RESPONSE>
      httpAttributesExtractorBuilder;
  private final HttpClientAttributesGetter<REQUEST, RESPONSE> attributesGetter;
  private final HttpSpanNameExtractorBuilder<REQUEST> httpSpanNameExtractorBuilder;
  private Function<SpanNameExtractor<REQUEST>, ? extends SpanNameExtractor<? super REQUEST>>
      spanNameExtractorTransformer = Function.identity();
  private boolean emitExperimentalHttpClientMetrics = false;

  protected HttpClientConfigBuilder(
      OpenTelemetry openTelemetry, HttpClientAttributesGetter<REQUEST, RESPONSE> attributesGetter) {
    this.openTelemetry = openTelemetry;
    httpSpanNameExtractorBuilder = HttpSpanNameExtractor.builder(attributesGetter);
    httpAttributesExtractorBuilder = HttpClientAttributesExtractor.builder(attributesGetter);
    this.attributesGetter = attributesGetter;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items. The {@link AttributesExtractor} will be executed after all default extractors.
   */
  @CanIgnoreReturnValue
  public SELF addAttributeExtractor(
      AttributesExtractor<? super REQUEST, ? super RESPONSE> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return self();
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public SELF setCapturedRequestHeaders(List<String> requestHeaders) {
    httpAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return self();
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public SELF setCapturedResponseHeaders(List<String> responseHeaders) {
    httpAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
    return self();
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
  public SELF setKnownMethods(Set<String> knownMethods) {
    httpAttributesExtractorBuilder.setKnownMethods(knownMethods);
    httpSpanNameExtractorBuilder.setKnownMethods(knownMethods);
    return self();
  }

  /**
   * Configures the instrumentation to emit experimental HTTP client metrics.
   *
   * @param emitExperimentalHttpClientMetrics {@code true} if the experimental HTTP client metrics
   *     are to be emitted.
   */
  @CanIgnoreReturnValue
  public SELF setEmitExperimentalHttpClientMetrics(boolean emitExperimentalHttpClientMetrics) {
    this.emitExperimentalHttpClientMetrics = emitExperimentalHttpClientMetrics;
    return self();
  }

  /** Sets custom {@link SpanNameExtractor} via transform function. */
  @CanIgnoreReturnValue
  public SELF setSpanNameExtractor(
      Function<SpanNameExtractor<REQUEST>, ? extends SpanNameExtractor<? super REQUEST>>
          spanNameExtractorTransformer) {
    this.spanNameExtractorTransformer = spanNameExtractorTransformer;
    return self();
  }

  /** Sets custom {@link PeerServiceResolver}. */
  @CanIgnoreReturnValue
  public SELF setPeerServiceResolver(PeerServiceResolver peerServiceResolver) {
    return addAttributeExtractor(
        HttpClientPeerServiceAttributesExtractor.create(attributesGetter, peerServiceResolver));
  }

  protected InstrumenterBuilder<REQUEST, RESPONSE> instrumenterBuilder(String instrumentationName) {
    SpanNameExtractor<? super REQUEST> spanNameExtractor =
        spanNameExtractorTransformer.apply(httpSpanNameExtractorBuilder.build());

    InstrumenterBuilder<REQUEST, RESPONSE> builder =
        Instrumenter.<REQUEST, RESPONSE>builder(
                openTelemetry, instrumentationName, spanNameExtractor)
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(attributesGetter))
            .addAttributesExtractor(httpAttributesExtractorBuilder.build())
            .addAttributesExtractors(additionalExtractors)
            .addOperationMetrics(HttpClientMetrics.get());
    if (emitExperimentalHttpClientMetrics) {
      builder
          .addAttributesExtractor(HttpExperimentalAttributesExtractor.create(attributesGetter))
          .addOperationMetrics(HttpClientExperimentalMetrics.get());
    }

    return builder;
  }

  @SuppressWarnings("unchecked")
  private SELF self() {
    return (SELF) this;
  }
}
