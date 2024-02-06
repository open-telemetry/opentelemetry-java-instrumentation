/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v5_2;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpClientExperimentalMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpExperimentalAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanStatusExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.hc.core5.http.HttpResponse;

/** A builder for {@link ApacheHttpClient5Telemetry}. */
public final class ApacheHttpClient5TelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-httpclient-5.2";

  private final OpenTelemetry openTelemetry;

  private final List<AttributesExtractor<? super ApacheHttpClient5Request, ? super HttpResponse>>
      additionalExtractors = new ArrayList<>();
  private final HttpClientAttributesExtractorBuilder<ApacheHttpClient5Request, HttpResponse>
      httpAttributesExtractorBuilder =
          HttpClientAttributesExtractor.builder(ApacheHttpClient5HttpAttributesGetter.INSTANCE);

  private final HttpSpanNameExtractorBuilder<ApacheHttpClient5Request>
      httpSpanNameExtractorBuilder =
          HttpSpanNameExtractor.builder(ApacheHttpClient5HttpAttributesGetter.INSTANCE);

  private boolean emitExperimentalHttpClientMetrics = false;

  ApacheHttpClient5TelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items. The {@link AttributesExtractor} will be executed after all default extractors.
   */
  @CanIgnoreReturnValue
  public ApacheHttpClient5TelemetryBuilder addAttributeExtractor(
      AttributesExtractor<? super ApacheHttpClient5Request, ? super HttpResponse>
          attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public ApacheHttpClient5TelemetryBuilder setCapturedRequestHeaders(List<String> requestHeaders) {
    httpAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public ApacheHttpClient5TelemetryBuilder setCapturedResponseHeaders(
      List<String> responseHeaders) {
    httpAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
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
  public ApacheHttpClient5TelemetryBuilder setKnownMethods(Set<String> knownMethods) {
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
  public ApacheHttpClient5TelemetryBuilder setEmitExperimentalHttpClientMetrics(
      boolean emitExperimentalHttpClientMetrics) {
    this.emitExperimentalHttpClientMetrics = emitExperimentalHttpClientMetrics;
    return this;
  }

  /**
   * Returns a new {@link ApacheHttpClient5Telemetry} configured with this {@link
   * ApacheHttpClient5TelemetryBuilder}.
   */
  public ApacheHttpClient5Telemetry build() {
    ApacheHttpClient5HttpAttributesGetter httpAttributesGetter =
        ApacheHttpClient5HttpAttributesGetter.INSTANCE;

    InstrumenterBuilder<ApacheHttpClient5Request, HttpResponse> builder =
        Instrumenter.<ApacheHttpClient5Request, HttpResponse>builder(
                openTelemetry, INSTRUMENTATION_NAME, httpSpanNameExtractorBuilder.build())
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(httpAttributesExtractorBuilder.build())
            .addAttributesExtractors(additionalExtractors)
            .addOperationMetrics(HttpClientMetrics.get());
    if (emitExperimentalHttpClientMetrics) {
      builder
          .addAttributesExtractor(HttpExperimentalAttributesExtractor.create(httpAttributesGetter))
          .addOperationMetrics(HttpClientExperimentalMetrics.get());
    }

    Instrumenter<ApacheHttpClient5Request, HttpResponse> instrumenter =
        builder
            // We manually inject because we need to inject internal requests for redirects.
            .buildInstrumenter(SpanKindExtractor.alwaysClient());

    return new ApacheHttpClient5Telemetry(instrumenter, openTelemetry.getPropagators());
  }
}
