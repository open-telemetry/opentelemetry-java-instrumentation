/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.annotations.UnstableApi;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcAttributesExtractor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A builder of {@link Instrumenter}. Instrumentation libraries should generally expose their own
 * builder with controls that are appropriate for that library and delegate to this to create the
 * {@link Instrumenter}.
 */
public final class InstrumenterBuilder<REQUEST, RESPONSE> {

  /** Instrumentation type suppression configuration property key. */
  private static final boolean ENABLE_SPAN_SUPPRESSION_BY_TYPE =
      Config.get()
          .getBoolean("otel.instrumentation.experimental.outgoing-span-suppression-by-type", false);

  final OpenTelemetry openTelemetry;
  final Meter meter;
  final String instrumentationName;
  final SpanNameExtractor<? super REQUEST> spanNameExtractor;

  final List<SpanLinksExtractor<? super REQUEST>> spanLinksExtractors = new ArrayList<>();
  final List<AttributesExtractor<? super REQUEST, ? super RESPONSE>> attributesExtractors =
      new ArrayList<>();
  final List<RequestListener> requestListeners = new ArrayList<>();

  SpanKindExtractor<? super REQUEST> spanKindExtractor = SpanKindExtractor.alwaysInternal();
  SpanStatusExtractor<? super REQUEST, ? super RESPONSE> spanStatusExtractor =
      SpanStatusExtractor.getDefault();
  ErrorCauseExtractor errorCauseExtractor = ErrorCauseExtractor.jdk();
  @Nullable StartTimeExtractor<REQUEST> startTimeExtractor = null;
  @Nullable EndTimeExtractor<REQUEST, RESPONSE> endTimeExtractor = null;
  boolean disabled = false;

  private boolean enableSpanSuppressionByType = ENABLE_SPAN_SUPPRESSION_BY_TYPE;

  InstrumenterBuilder(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      SpanNameExtractor<? super REQUEST> spanNameExtractor) {
    this.openTelemetry = openTelemetry;
    // TODO(anuraaga): Retrieve from openTelemetry when not alpha anymore.
    this.meter = GlobalMeterProvider.get().get(instrumentationName);
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

  /** Adds a {@link SpanLinksExtractor} to extract span links from requests. */
  public InstrumenterBuilder<REQUEST, RESPONSE> addSpanLinksExtractor(
      SpanLinksExtractor<REQUEST> spanLinksExtractor) {
    spanLinksExtractors.add(spanLinksExtractor);
    return this;
  }

  /** Adds a {@link RequestMetrics} whose metrics will be recorded for request start and stop. */
  @UnstableApi
  public InstrumenterBuilder<REQUEST, RESPONSE> addRequestMetrics(RequestMetrics factory) {
    requestListeners.add(factory.create(meter));
    return this;
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
      StartTimeExtractor<REQUEST> startTimeExtractor,
      EndTimeExtractor<REQUEST, RESPONSE> endTimeExtractor) {
    this.startTimeExtractor = requireNonNull(startTimeExtractor);
    this.endTimeExtractor = requireNonNull(endTimeExtractor);
    return this;
  }

  public InstrumenterBuilder<REQUEST, RESPONSE> setDisabled(boolean disabled) {
    this.disabled = disabled;
    return this;
  }

  // visible for tests
  /**
   * Enables suppression based on client instrumentation type.
   *
   * <p><strong>When enabled, suppresses nested spans depending on their {@link SpanKind} and
   * type</strong>.
   *
   * <ul>
   *   <li>CLIENT and PRODUCER nested spans are suppressed based on their type (HTTP, RPC, DB,
   *       MESSAGING) i.e. if span with the same type is on the context, new span of this type will
   *       not be started.
   * </ul>
   *
   * <p><strong>When disabled:</strong>
   *
   * <ul>
   *   <li>CLIENT and PRODUCER nested spans are always suppressed
   * </ul>
   *
   * <p><strong>In both cases:</strong>
   *
   * <ul>
   *   <li>SERVER and CONSUMER nested spans are always suppressed
   *   <li>INTERNAL spans are never suppressed
   * </ul>
   */
  InstrumenterBuilder<REQUEST, RESPONSE> enableInstrumentationTypeSuppression(
      boolean enableInstrumentationType) {
    this.enableSpanSuppressionByType = enableInstrumentationType;
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
    return newUpstreamPropagatingInstrumenter(SpanKindExtractor.alwaysServer(), getter);
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
    return newUpstreamPropagatingInstrumenter(SpanKindExtractor.alwaysConsumer(), getter);
  }

  /**
   * Returns a new {@link Instrumenter} which will create spans with kind determined by the passed
   * {@code spanKindExtractor} and extract context from requests.
   */
  public Instrumenter<REQUEST, RESPONSE> newUpstreamPropagatingInstrumenter(
      SpanKindExtractor<REQUEST> spanKindExtractor, TextMapGetter<REQUEST> getter) {
    return newInstrumenter(
        InstrumenterConstructor.propagatingFromUpstream(getter), spanKindExtractor);
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

  SpanSuppressionStrategy getSpanSuppressionStrategy() {
    if (!enableSpanSuppressionByType) {
      // if not enabled, preserve current behavior, not distinguishing types
      return SpanSuppressionStrategy.SUPPRESS_ALL_NESTED_OUTGOING_STRATEGY;
    }

    Set<SpanKey> spanKeys = spanKeysFromAttributeExtractor(this.attributesExtractors);
    return SpanSuppressionStrategy.from(spanKeys);
  }

  private static Set<SpanKey> spanKeysFromAttributeExtractor(
      List<? extends AttributesExtractor<?, ?>> attributesExtractors) {

    Set<SpanKey> spanKeys = new HashSet<>();
    for (AttributesExtractor<?, ?> attributeExtractor : attributesExtractors) {
      if (attributeExtractor instanceof HttpAttributesExtractor) {
        spanKeys.add(SpanKey.HTTP_CLIENT);
      } else if (attributeExtractor instanceof RpcAttributesExtractor) {
        spanKeys.add(SpanKey.RPC_CLIENT);
      } else if (attributeExtractor instanceof DbAttributesExtractor) {
        spanKeys.add(SpanKey.DB_CLIENT);
      } else if (attributeExtractor instanceof MessagingAttributesExtractor) {
        spanKeys.add(SpanKey.MESSAGING_PRODUCER);
      }
    }
    return spanKeys;
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
