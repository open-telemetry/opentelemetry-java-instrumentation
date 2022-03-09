/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.restlet.v2_0.internal.RestletHttpAttributesGetter;
import io.opentelemetry.instrumentation.restlet.v2_0.internal.RestletInstrumenterFactory;
import java.util.ArrayList;
import java.util.List;
import org.restlet.Request;
import org.restlet.Response;

/** A builder of {@link RestletTracing}. */
public final class RestletTracingBuilder {

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<Request, Response>> additionalExtractors =
      new ArrayList<>();
  private final HttpServerAttributesExtractorBuilder<Request, Response>
      httpAttributesExtractorBuilder =
          HttpServerAttributesExtractor.builder(RestletHttpAttributesGetter.INSTANCE);

  RestletTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  public RestletTracingBuilder addAttributesExtractor(
      AttributesExtractor<Request, Response> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configure the instrumentation to capture chosen HTTP request and response headers as span
   * attributes.
   *
   * @param capturedHttpHeaders An instance of {@link
   *     io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders} containing the
   *     configured HTTP request and response names.
   * @deprecated Use {@link #setCapturedRequestHeaders(List)} and {@link
   *     #setCapturedResponseHeaders(List)} instead.
   */
  @Deprecated
  public RestletTracingBuilder captureHttpHeaders(
      io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders
          capturedHttpHeaders) {
    httpAttributesExtractorBuilder.captureHttpHeaders(capturedHttpHeaders);
    return this;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  public RestletTracingBuilder setCapturedRequestHeaders(List<String> requestHeaders) {
    httpAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  public RestletTracingBuilder setCapturedResponseHeaders(List<String> responseHeaders) {
    httpAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /**
   * Returns a new {@link RestletTracing} with the settings of this {@link RestletTracingBuilder}.
   */
  public RestletTracing build() {
    Instrumenter<Request, Response> serverInstrumenter =
        RestletInstrumenterFactory.newServerInstrumenter(
            openTelemetry, httpAttributesExtractorBuilder.build(), additionalExtractors);

    return new RestletTracing(serverInstrumenter);
  }
}
