/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.logging.RequestLog;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public final class ArmeriaTracingBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.armeria-1.3";

  private final OpenTelemetry openTelemetry;

  private final List<AttributesExtractor<? super RequestContext, ? super RequestLog>>
      additionalExtractors = new ArrayList<>();

  private Function<
          SpanStatusExtractor<RequestContext, RequestLog>,
          ? extends SpanStatusExtractor<? super RequestContext, ? super RequestLog>>
      statusExtractorTransformer = Function.identity();

  ArmeriaTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  public ArmeriaTracingBuilder setStatusExtractor(
      Function<
              SpanStatusExtractor<RequestContext, RequestLog>,
              ? extends SpanStatusExtractor<? super RequestContext, ? super RequestLog>>
          statusExtractor) {
    this.statusExtractorTransformer = statusExtractor;
    return this;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items. The {@link AttributesExtractor} will be executed after all default extractors.
   */
  public ArmeriaTracingBuilder addAttributeExtractor(
      AttributesExtractor<? super RequestContext, ? super RequestLog> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  public ArmeriaTracing build() {
    ArmeriaHttpAttributesExtractor httpAttributesExtractor = new ArmeriaHttpAttributesExtractor();
    ArmeriaNetAttributesExtractor netAttributesExtractor = new ArmeriaNetAttributesExtractor();

    SpanNameExtractor<? super RequestContext> spanNameExtractor =
        HttpSpanNameExtractor.create(httpAttributesExtractor);
    SpanStatusExtractor<? super RequestContext, ? super RequestLog> spanStatusExtractor =
        statusExtractorTransformer.apply(HttpSpanStatusExtractor.create(httpAttributesExtractor));

    InstrumenterBuilder<ClientRequestContext, RequestLog> clientInstrumenterBuilder =
        Instrumenter.newBuilder(openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor);
    InstrumenterBuilder<ServiceRequestContext, RequestLog> serverInstrumenterBuilder =
        Instrumenter.newBuilder(openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor);

    Stream.of(clientInstrumenterBuilder, serverInstrumenterBuilder)
        .forEach(
            instrumenter ->
                instrumenter
                    .setSpanStatusExtractor(spanStatusExtractor)
                    .addAttributesExtractor(httpAttributesExtractor)
                    .addAttributesExtractor(netAttributesExtractor)
                    .addAttributesExtractors(additionalExtractors));

    serverInstrumenterBuilder.addRequestMetrics(HttpServerMetrics.get());

    return new ArmeriaTracing(
        clientInstrumenterBuilder.newClientInstrumenter(new ClientRequestContextSetter()),
        serverInstrumenterBuilder.newServerInstrumenter(new RequestContextGetter()));
  }
}
