/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v4_3;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;

/** A builder for {@link ApacheHttpClientTracing}. */
public final class ApacheHttpClientTracingBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-httpclient-4.3";

  private final OpenTelemetry openTelemetry;

  private final List<AttributesExtractor<? super HttpUriRequest, ? super HttpResponse>>
      additionalExtractors = new ArrayList<>();

  ApacheHttpClientTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items. The {@link AttributesExtractor} will be executed after all default extractors.
   */
  public ApacheHttpClientTracingBuilder addAttributeExtractor(
      AttributesExtractor<? super HttpUriRequest, ? super HttpResponse> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Returns a new {@link ApacheHttpClientTracing} configured with this {@link
   * ApacheHttpClientTracingBuilder}.
   */
  public ApacheHttpClientTracing build() {
    HttpAttributesExtractor<HttpUriRequest, HttpResponse> httpAttributesExtractor =
        new ApacheHttpClientHttpAttributesExtractor();
    SpanNameExtractor<? super HttpUriRequest> spanNameExtractor =
        HttpSpanNameExtractor.create(httpAttributesExtractor);
    SpanStatusExtractor<? super HttpUriRequest, ? super HttpResponse> spanStatusExtractor =
        HttpSpanStatusExtractor.create(httpAttributesExtractor);
    ApacheHttpClientNetAttributesExtractor netAttributesExtractor =
        new ApacheHttpClientNetAttributesExtractor();
    Instrumenter<HttpUriRequest, HttpResponse> instrumenter =
        Instrumenter.<HttpUriRequest, HttpResponse>newBuilder(
                openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor)
            .setSpanStatusExtractor(spanStatusExtractor)
            .addAttributesExtractor(httpAttributesExtractor)
            .addAttributesExtractor(netAttributesExtractor)
            .newClientInstrumenter(new HttpHeaderSetter());

    return new ApacheHttpClientTracing(instrumenter, openTelemetry.getPropagators());
  }
}
