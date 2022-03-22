/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;
import java.util.ArrayList;
import java.util.List;
import org.restlet.data.Request;
import org.restlet.data.Response;

/** A builder of {@link RestletTelemetry}. */
public final class RestletTelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.restlet-1.0";

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<Request, Response>> additionalExtractors =
      new ArrayList<>();
  private final HttpServerAttributesExtractorBuilder<Request, Response>
      httpAttributesExtractorBuilder =
          HttpServerAttributesExtractor.builder(RestletHttpAttributesGetter.INSTANCE);

  RestletTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  public RestletTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<Request, Response> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  public RestletTelemetryBuilder setCapturedRequestHeaders(List<String> requestHeaders) {
    httpAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  public RestletTelemetryBuilder setCapturedResponseHeaders(List<String> responseHeaders) {
    httpAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /**
   * Returns a new {@link RestletTelemetry} with the settings of this {@link
   * RestletTelemetryBuilder}.
   */
  public RestletTelemetry build() {
    RestletHttpAttributesGetter httpAttributesGetter = RestletHttpAttributesGetter.INSTANCE;
    RestletNetAttributesGetter netAttributesGetter = new RestletNetAttributesGetter();

    Instrumenter<Request, Response> instrumenter =
        Instrumenter.<Request, Response>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(httpAttributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(httpAttributesExtractorBuilder.build())
            .addAttributesExtractor(NetServerAttributesExtractor.create(netAttributesGetter))
            .addAttributesExtractors(additionalExtractors)
            .addRequestMetrics(HttpServerMetrics.get())
            .newServerInstrumenter(RestletHeadersGetter.INSTANCE);

    return new RestletTelemetry(instrumenter);
  }
}
