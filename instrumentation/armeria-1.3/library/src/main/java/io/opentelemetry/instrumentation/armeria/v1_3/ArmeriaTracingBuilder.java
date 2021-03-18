/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.logging.RequestLog;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.StatusExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class ArmeriaTracingBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.armeria-1.3";

  private static final SpanNameExtractor<RequestContext> DEFAULT_SPAN_NAME_EXTRACTOR =
      SpanNameExtractor.http(ArmeriaHttpAttributesExtractor.INSTANCE);
  private static final StatusExtractor<RequestContext, RequestLog> DEFAULT_STATUS_EXTRACTOR =
      StatusExtractor.http(ArmeriaHttpAttributesExtractor.INSTANCE);

  private final OpenTelemetry openTelemetry;

  private final List<AttributesExtractor<? super RequestContext, ? super RequestLog>>
      additionalExtractors = new ArrayList<>();

  private SpanNameExtractor<? super RequestContext> spanNameExtractor = DEFAULT_SPAN_NAME_EXTRACTOR;
  private StatusExtractor<? super RequestContext, ? super RequestLog> statusExtractor =
      DEFAULT_STATUS_EXTRACTOR;

  ArmeriaTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  public ArmeriaTracingBuilder setSpanNameExtractor(
      Function<
              SpanNameExtractor<RequestContext>,
              ? extends SpanNameExtractor<? super RequestContext>>
          spanNameExtractor) {
    this.spanNameExtractor = spanNameExtractor.apply(DEFAULT_SPAN_NAME_EXTRACTOR);
    return this;
  }

  public ArmeriaTracingBuilder setStatusExtractor(
      Function<
              StatusExtractor<RequestContext, RequestLog>,
              ? extends StatusExtractor<? super RequestContext, ? super RequestLog>>
          statusExtractor) {
    this.statusExtractor = statusExtractor.apply(DEFAULT_STATUS_EXTRACTOR);
    return this;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  public ArmeriaTracingBuilder addAttributeExtractor(
      AttributesExtractor<? super RequestContext, ? super RequestLog> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  public ArmeriaTracing build() {
    List<AttributesExtractor<? super RequestContext, ? super RequestLog>> attributesExtractors =
        new ArrayList<>();
    attributesExtractors.add(ArmeriaHttpAttributesExtractor.INSTANCE);
    attributesExtractors.add(ArmeriaNetAttributesExtractor.INSTANCE);
    attributesExtractors.addAll(additionalExtractors);

    ArmeriaClientInstrumenter clientInstrumenter =
        new ArmeriaClientInstrumenter(
            openTelemetry,
            INSTRUMENTATION_NAME,
            spanNameExtractor,
            statusExtractor,
            attributesExtractors);
    ArmeriaServerInstrumenter serverInstrumenter =
        new ArmeriaServerInstrumenter(
            openTelemetry,
            INSTRUMENTATION_NAME,
            spanNameExtractor,
            statusExtractor,
            attributesExtractors);

    return new ArmeriaTracing(clientInstrumenter, serverInstrumenter);
  }
}
