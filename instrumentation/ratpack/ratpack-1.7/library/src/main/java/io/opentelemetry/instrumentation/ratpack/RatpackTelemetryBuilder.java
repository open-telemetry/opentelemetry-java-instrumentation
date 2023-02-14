/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.ratpack.internal.RatpackNetClientAttributesGetter;
import io.opentelemetry.instrumentation.ratpack.internal.RatpackNetServerAttributesGetter;
import java.util.ArrayList;
import java.util.List;
import ratpack.http.Request;
import ratpack.http.Response;
import ratpack.http.client.HttpResponse;
import ratpack.http.client.RequestSpec;

/** A builder for {@link RatpackTelemetry}. */
public final class RatpackTelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.ratpack-1.7";

  private final OpenTelemetry openTelemetry;

  private final List<AttributesExtractor<? super Request, ? super Response>> additionalExtractors =
      new ArrayList<>();
  private final HttpClientAttributesExtractorBuilder<RequestSpec, HttpResponse>
      httpClientAttributesExtractorBuilder =
          HttpClientAttributesExtractor.builder(
              RatpackHttpClientAttributesGetter.INSTANCE, new RatpackNetClientAttributesGetter());
  private final HttpServerAttributesExtractorBuilder<Request, Response>
      httpServerAttributesExtractorBuilder =
          HttpServerAttributesExtractor.builder(
              RatpackHttpAttributesGetter.INSTANCE, new RatpackNetServerAttributesGetter());

  private final List<AttributesExtractor<? super RequestSpec, ? super HttpResponse>>
      additionalHttpClientExtractors = new ArrayList<>();

  RatpackTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items. The {@link AttributesExtractor} will be executed after all default extractors.
   */
  @CanIgnoreReturnValue
  public RatpackTelemetryBuilder addAttributeExtractor(
      AttributesExtractor<? super Request, ? super Response> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  @CanIgnoreReturnValue
  public RatpackTelemetryBuilder addClientAttributeExtractor(
      AttributesExtractor<? super RequestSpec, ? super HttpResponse> attributesExtractor) {
    additionalHttpClientExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configures the HTTP server request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public RatpackTelemetryBuilder setCapturedServerRequestHeaders(List<String> requestHeaders) {
    httpServerAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP server response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public RatpackTelemetryBuilder setCapturedServerResponseHeaders(List<String> responseHeaders) {
    httpServerAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /**
   * Configures the HTTP client request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public RatpackTelemetryBuilder setCapturedClientRequestHeaders(List<String> requestHeaders) {
    httpClientAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP client response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public RatpackTelemetryBuilder setCapturedClientResponseHeaders(List<String> responseHeaders) {
    httpClientAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /** Returns a new {@link RatpackTelemetry} with the configuration of this builder. */
  public RatpackTelemetry build() {
    RatpackHttpAttributesGetter httpAttributes = RatpackHttpAttributesGetter.INSTANCE;

    Instrumenter<Request, Response> instrumenter =
        Instrumenter.<Request, Response>builder(
                openTelemetry, INSTRUMENTATION_NAME, HttpSpanNameExtractor.create(httpAttributes))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributes))
            .addAttributesExtractor(httpServerAttributesExtractorBuilder.build())
            .addAttributesExtractors(additionalExtractors)
            .addOperationMetrics(HttpServerMetrics.get())
            .addContextCustomizer(HttpRouteHolder.create(httpAttributes))
            .buildServerInstrumenter(RatpackGetter.INSTANCE);

    return new RatpackTelemetry(instrumenter, httpClientInstrumenter());
  }

  private Instrumenter<RequestSpec, HttpResponse> httpClientInstrumenter() {
    RatpackHttpClientAttributesGetter httpAttributes = RatpackHttpClientAttributesGetter.INSTANCE;

    return Instrumenter.<RequestSpec, HttpResponse>builder(
            openTelemetry, INSTRUMENTATION_NAME, HttpSpanNameExtractor.create(httpAttributes))
        .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributes))
        .addAttributesExtractor(httpClientAttributesExtractorBuilder.build())
        .addAttributesExtractors(additionalHttpClientExtractors)
        .addOperationMetrics(HttpServerMetrics.get())
        .buildClientInstrumenter(RequestHeaderSetter.INSTANCE);
  }
}
