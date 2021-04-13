/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.InstrumentationVersion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A builder of {@link Instrumenter}. Instrumentation libraries should generally expose their own
 * builder with controls that are appropriate for that library and delegate to this to create the
 * {@link Instrumenter}.
 */
public final class InstrumenterBuilder<REQUEST, RESPONSE> {
  private final OpenTelemetry openTelemetry;
  private final String instrumentationName;
  private final SpanNameExtractor<? super REQUEST> spanNameExtractor;

  private final List<AttributesExtractor<? super REQUEST, ? super RESPONSE>> attributesExtractors =
      new ArrayList<>();

  private SpanStatusExtractor<? super REQUEST, ? super RESPONSE> spanStatusExtractor =
      SpanStatusExtractor.getDefault();
  private ErrorCauseExtractor errorCauseExtractor = ErrorCauseExtractor.jdk();

  InstrumenterBuilder(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      SpanNameExtractor<? super REQUEST> spanNameExtractor) {
    this.openTelemetry = openTelemetry;
    this.instrumentationName = instrumentationName;
    this.spanNameExtractor = spanNameExtractor;
  }

  /**
   * Sets the {@link SpanStatusExtractor} to use to determine the {@link StatusCode} for a response.
   */
  public InstrumenterBuilder<REQUEST, RESPONSE> setSpanStatusExtractor(
      SpanStatusExtractor<? super REQUEST, ? super RESPONSE> spanStatusExtractor) {
    this.spanStatusExtractor = spanStatusExtractor;
    return this;
  }

  /** Adds a {@link AttributesExtractor} to extract attributes from requests and responses. */
  public InstrumenterBuilder<REQUEST, RESPONSE> addAttributesExtractor(
      AttributesExtractor<? super REQUEST, ? super RESPONSE> attributesExtractor) {
    this.attributesExtractors.add(attributesExtractor);
    return this;
  }

  /** Adds {@link AttributesExtractor}s to extract attributes from requests and responses. */
  public InstrumenterBuilder<REQUEST, RESPONSE> addAttributesExtractors(
      Iterable<? extends AttributesExtractor<? super REQUEST, ? super RESPONSE>>
          attributesExtractors) {
    attributesExtractors.forEach(this.attributesExtractors::add);
    return this;
  }

  /** Adds {@link AttributesExtractor}s to extract attributes from requests and responses. */
  public InstrumenterBuilder<REQUEST, RESPONSE> addAttributesExtractors(
      AttributesExtractor<? super REQUEST, ? super RESPONSE>... attributesExtractors) {
    return addAttributesExtractors(Arrays.asList(attributesExtractors));
  }

  /**
   * Sets the {@link ErrorCauseExtractor} to extract the root cause from an exception handling the
   * request.
   */
  public InstrumenterBuilder<REQUEST, RESPONSE> setErrorCauseExtractor(
      ErrorCauseExtractor errorCauseExtractor) {
    this.errorCauseExtractor = errorCauseExtractor;
    return this;
  }

  /**
   * Returns a new client {@link Instrumenter} which will create client spans and inject context
   * into requests.
   */
  public Instrumenter<REQUEST, RESPONSE> newClientInstrumenter(TextMapSetter<REQUEST> setter) {
    return newInstrumenter(
        InstrumenterConstructor.propagatingToDownstream(openTelemetry.getPropagators(), setter),
        SpanKindExtractor.alwaysClient());
  }

  /**
   * Returns a new server {@link Instrumenter} which will create server spans and extract context
   * from requests.
   */
  public Instrumenter<REQUEST, RESPONSE> newServerInstrumenter(TextMapGetter<REQUEST> getter) {
    return newInstrumenter(
        InstrumenterConstructor.propagatingFromUpstream(openTelemetry.getPropagators(), getter),
        SpanKindExtractor.alwaysServer());
  }

  /**
   * Returns a new {@link Instrumenter} which will create internal spans and do no context
   * propagation.
   */
  public Instrumenter<REQUEST, RESPONSE> newInstrumenter() {
    return newInstrumenter(InstrumenterConstructor.internal(), SpanKindExtractor.alwaysInternal());
  }

  /**
   * Returns a new {@link Instrumenter} which will create spans with kind determined by the passed
   * {@code spanKindExtractor} and do no context propagation.
   */
  public Instrumenter<REQUEST, RESPONSE> newInstrumenter(
      SpanKindExtractor<? super REQUEST> spanKindExtractor) {
    return newInstrumenter(InstrumenterConstructor.internal(), spanKindExtractor);
  }

  private Instrumenter<REQUEST, RESPONSE> newInstrumenter(
      InstrumenterConstructor<REQUEST, RESPONSE> constructor,
      SpanKindExtractor<? super REQUEST> spanKindExtractor) {
    return constructor.create(
        instrumentationName,
        openTelemetry.getTracer(instrumentationName, InstrumentationVersion.VERSION),
        spanNameExtractor,
        spanKindExtractor,
        spanStatusExtractor,
        new ArrayList<>(attributesExtractors),
        errorCauseExtractor);
  }

  private interface InstrumenterConstructor<RQ, RS> {
    Instrumenter<RQ, RS> create(
        String instrumentationName,
        Tracer tracer,
        SpanNameExtractor<? super RQ> spanNameExtractor,
        SpanKindExtractor<? super RQ> spanKindExtractor,
        SpanStatusExtractor<? super RQ, ? super RS> spanStatusExtractor,
        List<? extends AttributesExtractor<? super RQ, ? super RS>> extractors,
        ErrorCauseExtractor errorCauseExtractor);

    static <RQ, RS> InstrumenterConstructor<RQ, RS> internal() {
      return Instrumenter::new;
    }

    static <RQ, RS> InstrumenterConstructor<RQ, RS> propagatingToDownstream(
        ContextPropagators propagators, TextMapSetter<RQ> setter) {
      return (instrumentationName,
          tracer,
          spanName,
          spanKind,
          spanStatus,
          attributes,
          errorCauseExtractor) ->
          new ClientInstrumenter<>(
              instrumentationName,
              tracer,
              spanName,
              spanKind,
              spanStatus,
              attributes,
              errorCauseExtractor,
              propagators,
              setter);
    }

    static <RQ, RS> InstrumenterConstructor<RQ, RS> propagatingFromUpstream(
        ContextPropagators propagators, TextMapGetter<RQ> getter) {
      return (instrumentationName,
          tracer,
          spanName,
          spanKind,
          spanStatus,
          attributes,
          errorCauseExtractor) ->
          new ServerInstrumenter<>(
              instrumentationName,
              tracer,
              spanName,
              spanKind,
              spanStatus,
              attributes,
              errorCauseExtractor,
              propagators,
              getter);
    }
  }
}
