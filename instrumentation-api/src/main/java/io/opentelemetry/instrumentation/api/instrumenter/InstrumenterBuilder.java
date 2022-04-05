/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
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
  final String instrumentationName;
  final SpanNameExtractor<? super REQUEST> spanNameExtractor;

  final List<SpanLinksExtractor<? super REQUEST>> spanLinksExtractors = new ArrayList<>();
  final List<AttributesExtractor<? super REQUEST, ? super RESPONSE>> attributesExtractors =
      new ArrayList<>();
  final List<ContextCustomizer<? super REQUEST>> contextCustomizers = new ArrayList<>();
  private final List<RequestListener> requestListeners = new ArrayList<>();
  private final List<RequestMetrics> requestMetrics = new ArrayList<>();

  private String instrumentationVersion;
  @Nullable private String schemaUrl = null;
  SpanKindExtractor<? super REQUEST> spanKindExtractor = SpanKindExtractor.alwaysInternal();
  SpanStatusExtractor<? super REQUEST, ? super RESPONSE> spanStatusExtractor =
      SpanStatusExtractor.getDefault();
  ErrorCauseExtractor errorCauseExtractor = ErrorCauseExtractor.jdk();
  @Nullable TimeExtractor<REQUEST, RESPONSE> timeExtractor = null;
  boolean enabled = true;

  private boolean enableSpanSuppressionByType = ENABLE_SPAN_SUPPRESSION_BY_TYPE;

  InstrumenterBuilder(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      SpanNameExtractor<? super REQUEST> spanNameExtractor) {
    this.openTelemetry = openTelemetry;
    this.instrumentationName = instrumentationName;
    this.instrumentationVersion =
        EmbeddedInstrumentationProperties.findVersion(instrumentationName);
    this.spanNameExtractor = spanNameExtractor;
  }

  /**
   * Sets the instrumentation version that'll be associated with all telemetry produced by this
   * {@link Instrumenter}.
   *
   * @param instrumentationVersion is the version of the instrumentation library, not the version of
   *     the instrument*ed* library.
   */
  public InstrumenterBuilder<REQUEST, RESPONSE> setInstrumentationVersion(
      String instrumentationVersion) {
    this.instrumentationVersion = instrumentationVersion;
    return this;
  }

  /**
   * Sets the OpenTelemetry schema URL that'll be associated with all telemetry produced by this
   * {@link Instrumenter}.
   */
  public InstrumenterBuilder<REQUEST, RESPONSE> setSchemaUrl(String schemaUrl) {
    this.schemaUrl = schemaUrl;
    return this;
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

  /** Adds a {@link RequestListener} which will be called for request start and end. */
  public InstrumenterBuilder<REQUEST, RESPONSE> addRequestListener(RequestListener listener) {
    requestListeners.add(listener);
    return this;
  }

  /** Adds a {@link RequestMetrics} whose metrics will be recorded for request start and end. */
  public InstrumenterBuilder<REQUEST, RESPONSE> addRequestMetrics(RequestMetrics factory) {
    requestMetrics.add(factory);
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

  /**
   * Allows enabling/disabling the {@link Instrumenter} based on the {@code enabled} value passed as
   * parameter. All instrumenters are enabled by default.
   */
  public InstrumenterBuilder<REQUEST, RESPONSE> setEnabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  /**
   * Allows to disable the {@link Instrumenter}.
   *
   * @deprecated Use {@link #setEnabled(boolean)} instead.
   */
  @Deprecated
  public InstrumenterBuilder<REQUEST, RESPONSE> setDisabled(boolean disabled) {
    return setEnabled(!disabled);
  }

  // visible for tests
  /**
   * Enables CLIENT nested span suppression based on the instrumentation type.
   *
   * <p><strong>When enabled:</strong>.
   *
   * <ul>
   *   <li>CLIENT nested spans are suppressed depending on their type: {@code
   *       HttpClientAttributesExtractor HTTP}, {@code RpcClientAttributesExtractor RPC} or {@code
   *       DbClientAttributesExtractor database} clients. If a span with the same type is present in
   *       the parent context object, new span of the same type will not be started.
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
   *   <li>Messaging (PRODUCER and CONSUMER) nested spans are suppressed depending on their {@code
   *       MessageOperation operation}. If a span with the same operation is present in the parent
   *       context object, new span with the same operation will not be started.
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

  Tracer buildTracer() {
    TracerBuilder tracerBuilder =
        openTelemetry
            .getTracerProvider()
            .tracerBuilder(instrumentationName)
            .setInstrumentationVersion(instrumentationVersion);
    if (schemaUrl != null) {
      tracerBuilder.setSchemaUrl(schemaUrl);
    }
    return tracerBuilder.build();
  }

  List<RequestListener> buildRequestListeners() {
    List<RequestListener> listeners =
        new ArrayList<>(requestListeners.size() + requestMetrics.size());
    listeners.addAll(requestListeners);

    MeterBuilder meterBuilder =
        openTelemetry
            .getMeterProvider()
            .meterBuilder(instrumentationName)
            .setInstrumentationVersion(instrumentationVersion);
    if (schemaUrl != null) {
      meterBuilder.setSchemaUrl(schemaUrl);
    }
    Meter meter = meterBuilder.build();
    for (RequestMetrics factory : requestMetrics) {
      listeners.add(factory.create(meter));
    }

    return listeners;
  }

  SpanSuppressionStrategy buildSpanSuppressionStrategy() {
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
