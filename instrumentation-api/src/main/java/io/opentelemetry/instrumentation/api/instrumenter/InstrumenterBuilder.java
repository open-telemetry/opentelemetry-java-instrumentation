/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A builder of {@link Instrumenter}. Instrumentation libraries should generally expose their own
 * builder with controls that are appropriate for that library and delegate to this to create the
 * {@link Instrumenter}.
 */
public final class InstrumenterBuilder<REQUEST, RESPONSE> {
  final OpenTelemetry openTelemetry;
  final String instrumentationName;
  final SpanNameExtractor<? super REQUEST> spanNameExtractor;

  final List<AttributesExtractor<? super REQUEST, ? super RESPONSE>> attributesExtractors =
      new ArrayList<>();

  SpanKindExtractor<? super REQUEST> spanKindExtractor;
  SpanStatusExtractor<? super REQUEST, ? super RESPONSE> spanStatusExtractor =
      SpanStatusExtractor.getDefault();
  ErrorCauseExtractor errorCauseExtractor = ErrorCauseExtractor.jdk();
  StartTimeExtractor<REQUEST> startTimeExtractor = null;
  EndTimeExtractor<RESPONSE> endTimeExtractor = null;

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
   * Sets the {@link StartTimeExtractor} and the {@link EndTimeExtractor} to extract the timestamp
   * marking the start and end of processing. If unset, the constructed instrumenter will defer
   * determining start and end timestamps to the OpenTelemetry SDK.
   */
  public InstrumenterBuilder<REQUEST, RESPONSE> setTimeExtractors(
      StartTimeExtractor<REQUEST> startTimeExtractor, EndTimeExtractor<RESPONSE> endTimeExtractor) {
    this.startTimeExtractor = requireNonNull(startTimeExtractor);
    this.endTimeExtractor = requireNonNull(endTimeExtractor);
    return this;
  }

  /**
   * Returns a new {@link Instrumenter} which will create client spans and inject context into
   * requests.
   */
  public Instrumenter<REQUEST, RESPONSE> newClientInstrumenter(TextMapSetter<REQUEST> setter) {
    return newInstrumenter(
        InstrumenterConstructor.propagatingToDownstream(setter), SpanKindExtractor.alwaysClient());
  }

  /**
   * Returns a new {@link Instrumenter} which will create server spans and extract context from
   * requests.
   */
  public Instrumenter<REQUEST, RESPONSE> newServerInstrumenter(TextMapGetter<REQUEST> getter) {
    return newInstrumenter(
        InstrumenterConstructor.propagatingFromUpstream(getter), SpanKindExtractor.alwaysServer());
  }

  /**
   * Returns a new {@link Instrumenter} which will create producer spans and inject context into
   * requests.
   */
  public Instrumenter<REQUEST, RESPONSE> newProducerInstrumenter(TextMapSetter<REQUEST> setter) {
    return newInstrumenter(
        InstrumenterConstructor.propagatingToDownstream(setter),
        SpanKindExtractor.alwaysProducer());
  }

  /**
   * Returns a new {@link Instrumenter} which will create consumer spans and extract context from
   * requests.
   */
  public Instrumenter<REQUEST, RESPONSE> newConsumerInstrumenter(TextMapGetter<REQUEST> getter) {
    return newInstrumenter(
        InstrumenterConstructor.propagatingFromUpstream(getter),
        SpanKindExtractor.alwaysConsumer());
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
    this.spanKindExtractor = spanKindExtractor;
    return constructor.create(this);
  }

  private interface InstrumenterConstructor<RQ, RS> {
    Instrumenter<RQ, RS> create(InstrumenterBuilder<RQ, RS> builder);

    static <RQ, RS> InstrumenterConstructor<RQ, RS> internal() {
      return Instrumenter::new;
    }

    static <RQ, RS> InstrumenterConstructor<RQ, RS> propagatingToDownstream(
        TextMapSetter<RQ> setter) {
      return builder -> new ClientInstrumenter<>(builder, setter);
    }

    static <RQ, RS> InstrumenterConstructor<RQ, RS> propagatingFromUpstream(
        TextMapGetter<RQ> getter) {
      return builder -> new ServerInstrumenter<>(builder, getter);
    }
  }
}
