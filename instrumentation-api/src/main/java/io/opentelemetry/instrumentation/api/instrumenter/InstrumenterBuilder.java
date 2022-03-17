/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.annotations.UnstableApi;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.internal.SpanKeyProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

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
  final String instrumentationVersion;
  final SpanNameExtractor<? super REQUEST> spanNameExtractor;

  final List<SpanLinksExtractor<? super REQUEST>> spanLinksExtractors = new ArrayList<>();
  final List<AttributesExtractor<? super REQUEST, ? super RESPONSE>> attributesExtractors =
      new ArrayList<>();
  final List<ContextCustomizer<? super REQUEST>> contextCustomizers = new ArrayList<>();
  final List<RequestListener> requestListeners = new ArrayList<>();
  final List<RequestListener> requestMetricListeners = new ArrayList<>();

  SpanKindExtractor<? super REQUEST> spanKindExtractor = SpanKindExtractor.alwaysInternal();
  SpanStatusExtractor<? super REQUEST, ? super RESPONSE> spanStatusExtractor =
      SpanStatusExtractor.getDefault();
  ErrorCauseExtractor errorCauseExtractor = ErrorCauseExtractor.jdk();
  @Nullable TimeExtractor<REQUEST, RESPONSE> timeExtractor = null;
  boolean disabled = false;

  private boolean enableSpanSuppressionByType = ENABLE_SPAN_SUPPRESSION_BY_TYPE;

  InstrumenterBuilder(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      String instrumentationVersion,
      SpanNameExtractor<? super REQUEST> spanNameExtractor) {
    this.openTelemetry = openTelemetry;
    this.meter = openTelemetry.getMeterProvider().get(instrumentationName);
    this.instrumentationName = instrumentationName;
    this.instrumentationVersion = instrumentationVersion;
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
  @SafeVarargs
  @SuppressWarnings("varargs")
  public final InstrumenterBuilder<REQUEST, RESPONSE> addAttributesExtractors(
      AttributesExtractor<? super REQUEST, ? super RESPONSE>... attributesExtractors) {
    return addAttributesExtractors(Arrays.asList(attributesExtractors));
  }

  /** Adds a {@link SpanLinksExtractor} to extract span links from requests. */
  public InstrumenterBuilder<REQUEST, RESPONSE> addSpanLinksExtractor(
      SpanLinksExtractor<REQUEST> spanLinksExtractor) {
    spanLinksExtractors.add(spanLinksExtractor);
    return this;
  }

  /**
   * Adds a {@link ContextCustomizer} to customize the context during {@link
   * Instrumenter#start(Context, Object)}.
   */
  public InstrumenterBuilder<REQUEST, RESPONSE> addContextCustomizer(
      ContextCustomizer<? super REQUEST> contextCustomizer) {
    contextCustomizers.add(contextCustomizer);
    return this;
  }

  /** Adds a {@link RequestMetrics} whose metrics will be recorded for request start and stop. */
  @UnstableApi
  public InstrumenterBuilder<REQUEST, RESPONSE> addRequestMetrics(RequestMetrics factory) {
    requestMetricListeners.add(factory.create(meter));
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
   * Sets the {@link TimeExtractor} to extract the timestamp marking the start and end of
   * processing. If unset, the constructed instrumenter will defer determining start and end
   * timestamps to the OpenTelemetry SDK.
   *
   * <p>Note: if metrics are generated by the Instrumenter, the start and end times from the {@link
   * TimeExtractor} will be used to generate any duration metrics, but the internal metric timestamp
   * (when it occurred) will always be stamped with "now" when the metric is recorded (i.e. there is
   * no way to back date a metric recording).
   */
  public InstrumenterBuilder<REQUEST, RESPONSE> setTimeExtractor(
      TimeExtractor<REQUEST, RESPONSE> timeExtractor) {
    this.timeExtractor = requireNonNull(timeExtractor);
    return this;
  }

  public InstrumenterBuilder<REQUEST, RESPONSE> setDisabled(boolean disabled) {
    this.disabled = disabled;
    return this;
  }

  // visible for tests
  /**
   * Enables CLIENT nested span suppression based on the instrumentation type.
   *
   * <p><strong>When enabled:</strong>.
   *
   * <ul>
   *   <li>CLIENT nested spans are suppressed depending on their type: {@linkplain
   *       HttpClientAttributesExtractor HTTP}, {@linkplain RpcClientAttributesExtractor RPC} or
   *       {@linkplain DbClientAttributesExtractor database} clients. If a span with the same type
   *       is present in the parent context object, new span of the same type will not be started.
   * </ul>
   *
   * <p><strong>When disabled:</strong>
   *
   * <ul>
   *   <li>CLIENT nested spans are always suppressed.
   * </ul>
   *
   * <p>For all other {@linkplain SpanKind span kinds} the suppression rules are as follows:
   *
   * <ul>
   *   <li>SERVER nested spans are always suppressed. If a SERVER span is present in the parent
   *       context object, new SERVER span will not be started.
   *   <li>Messaging (PRODUCER and CONSUMER) nested spans are suppressed depending on their
   *       {@linkplain MessagingAttributesExtractor#operation() operation}. If a span with the same
   *       operation is present in the parent context object, new span with the same operation will
   *       not be started.
   *   <li>INTERNAL spans are never suppressed.
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

  SpanSuppressionStrategy getSpanSuppressionStrategy() {
    Set<SpanKey> spanKeys = getSpanKeysFromAttributesExtractors();
    if (enableSpanSuppressionByType) {
      return SpanSuppressionStrategy.from(spanKeys);
    }
    // if not enabled, preserve current behavior, not distinguishing CLIENT instrumentation types
    return SpanSuppressionStrategy.suppressNestedClients(spanKeys);
  }

  private Set<SpanKey> getSpanKeysFromAttributesExtractors() {
    return attributesExtractors.stream()
        .filter(SpanKeyProvider.class::isInstance)
        .map(SpanKeyProvider.class::cast)
        .flatMap(
            provider -> {
              SpanKey spanKey = provider.internalGetSpanKey();
              return spanKey == null ? Stream.of() : Stream.of(spanKey);
            })
        .collect(Collectors.toSet());
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
