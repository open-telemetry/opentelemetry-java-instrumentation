/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.WARNING;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
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
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import io.opentelemetry.instrumentation.api.internal.InstrumenterBuilderAccess;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.SchemaUrlProvider;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.internal.SpanKeyProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * A builder of an {@link Instrumenter}.
 *
 * <p>Instrumentation libraries should generally expose their own builder with controls that are
 * appropriate for that library and delegate to this class to create the {@link Instrumenter}.
 */
public final class InstrumenterBuilder<REQUEST, RESPONSE> {

  private static final Logger logger = Logger.getLogger(InstrumenterBuilder.class.getName());

  private static final SpanSuppressionStrategy spanSuppressionStrategy =
      SpanSuppressionStrategy.fromConfig(
          ConfigPropertiesUtil.getString(
              "otel.instrumentation.experimental.span-suppression-strategy"));

  final OpenTelemetry openTelemetry;
  final String instrumentationName;
  final SpanNameExtractor<? super REQUEST> spanNameExtractor;

  final List<SpanLinksExtractor<? super REQUEST>> spanLinksExtractors = new ArrayList<>();
  final List<AttributesExtractor<? super REQUEST, ? super RESPONSE>> attributesExtractors =
      new ArrayList<>();
  final List<ContextCustomizer<? super REQUEST>> contextCustomizers = new ArrayList<>();
  private final List<OperationListener> operationListeners = new ArrayList<>();
  private final List<OperationMetrics> operationMetrics = new ArrayList<>();

  @Nullable private String instrumentationVersion;
  @Nullable private String schemaUrl = null;
  SpanKindExtractor<? super REQUEST> spanKindExtractor = SpanKindExtractor.alwaysInternal();
  SpanStatusExtractor<? super REQUEST, ? super RESPONSE> spanStatusExtractor =
      SpanStatusExtractor.getDefault();
  ErrorCauseExtractor errorCauseExtractor = ErrorCauseExtractor.getDefault();
  boolean propagateOperationListenersToOnEnd = false;
  boolean enabled = true;

  InstrumenterBuilder(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      SpanNameExtractor<? super REQUEST> spanNameExtractor) {
    this.openTelemetry = openTelemetry;
    this.instrumentationName = instrumentationName;
    this.spanNameExtractor = spanNameExtractor;
    this.instrumentationVersion =
        EmbeddedInstrumentationProperties.findVersion(instrumentationName);
  }

  /**
   * Sets the instrumentation version that will be associated with all telemetry produced by this
   * {@link Instrumenter}.
   *
   * @param instrumentationVersion is the version of the instrumentation library, not the version of
   *     the instrument<b>ed</b> library.
   */
  @CanIgnoreReturnValue
  public InstrumenterBuilder<REQUEST, RESPONSE> setInstrumentationVersion(
      String instrumentationVersion) {
    this.instrumentationVersion = requireNonNull(instrumentationVersion, "instrumentationVersion");
    return this;
  }

  /**
   * Sets the OpenTelemetry schema URL that will be associated with all telemetry produced by this
   * {@link Instrumenter}.
   */
  @CanIgnoreReturnValue
  public InstrumenterBuilder<REQUEST, RESPONSE> setSchemaUrl(String schemaUrl) {
    this.schemaUrl = requireNonNull(schemaUrl, "schemaUrl");
    return this;
  }

  /**
   * Sets the {@link SpanStatusExtractor} that will determine the {@link StatusCode} for a response.
   */
  @CanIgnoreReturnValue
  public InstrumenterBuilder<REQUEST, RESPONSE> setSpanStatusExtractor(
      SpanStatusExtractor<? super REQUEST, ? super RESPONSE> spanStatusExtractor) {
    this.spanStatusExtractor = requireNonNull(spanStatusExtractor, "spanStatusExtractor");
    return this;
  }

  /**
   * Adds a {@link AttributesExtractor} that will extract attributes from requests and responses.
   */
  @CanIgnoreReturnValue
  public InstrumenterBuilder<REQUEST, RESPONSE> addAttributesExtractor(
      AttributesExtractor<? super REQUEST, ? super RESPONSE> attributesExtractor) {
    this.attributesExtractors.add(requireNonNull(attributesExtractor, "attributesExtractor"));
    return this;
  }

  /** Adds {@link AttributesExtractor}s that will extract attributes from requests and responses. */
  @CanIgnoreReturnValue
  public InstrumenterBuilder<REQUEST, RESPONSE> addAttributesExtractors(
      Iterable<? extends AttributesExtractor<? super REQUEST, ? super RESPONSE>>
          attributesExtractors) {
    attributesExtractors.forEach(this::addAttributesExtractor);
    return this;
  }

  /** Adds a {@link SpanLinksExtractor} that will extract span links from requests. */
  @CanIgnoreReturnValue
  public InstrumenterBuilder<REQUEST, RESPONSE> addSpanLinksExtractor(
      SpanLinksExtractor<REQUEST> spanLinksExtractor) {
    spanLinksExtractors.add(requireNonNull(spanLinksExtractor, "spanLinksExtractor"));
    return this;
  }

  /**
   * Adds a {@link ContextCustomizer} that will customize the context during {@link
   * Instrumenter#start(Context, Object)}.
   */
  @CanIgnoreReturnValue
  public InstrumenterBuilder<REQUEST, RESPONSE> addContextCustomizer(
      ContextCustomizer<? super REQUEST> contextCustomizer) {
    contextCustomizers.add(requireNonNull(contextCustomizer, "contextCustomizer"));
    return this;
  }

  /**
   * Adds a {@link OperationListener} that will be called when an instrumented operation starts and
   * ends.
   */
  @CanIgnoreReturnValue
  public InstrumenterBuilder<REQUEST, RESPONSE> addOperationListener(OperationListener listener) {
    operationListeners.add(requireNonNull(listener, "operationListener"));
    return this;
  }

  /**
   * Adds a {@link OperationMetrics} that will produce a {@link OperationListener} capturing the
   * requests processing metrics.
   */
  @CanIgnoreReturnValue
  public InstrumenterBuilder<REQUEST, RESPONSE> addOperationMetrics(OperationMetrics factory) {
    operationMetrics.add(requireNonNull(factory, "operationMetrics"));
    return this;
  }

  /**
   * Sets the {@link ErrorCauseExtractor} that will extract the root cause of an error thrown during
   * request processing.
   */
  @CanIgnoreReturnValue
  public InstrumenterBuilder<REQUEST, RESPONSE> setErrorCauseExtractor(
      ErrorCauseExtractor errorCauseExtractor) {
    this.errorCauseExtractor = requireNonNull(errorCauseExtractor, "errorCauseExtractor");
    return this;
  }

  /**
   * Allows enabling/disabling the {@link Instrumenter} based on the {@code enabled} value passed as
   * parameter. All instrumenters are enabled by default.
   */
  @CanIgnoreReturnValue
  public InstrumenterBuilder<REQUEST, RESPONSE> setEnabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  /**
   * Returns a new {@link Instrumenter} which will create {@linkplain SpanKind#CLIENT client} spans
   * and inject context into requests with the passed {@link TextMapSetter}.
   */
  public Instrumenter<REQUEST, RESPONSE> buildClientInstrumenter(TextMapSetter<REQUEST> setter) {
    return buildInstrumenter(
        InstrumenterConstructor.propagatingToDownstream(requireNonNull(setter, "setter")),
        SpanKindExtractor.alwaysClient());
  }

  /**
   * Returns a new {@link Instrumenter} which will create {@linkplain SpanKind#SERVER server} spans
   * and extract context from requests with the passed {@link TextMapGetter}.
   */
  public Instrumenter<REQUEST, RESPONSE> buildServerInstrumenter(TextMapGetter<REQUEST> getter) {
    return buildInstrumenter(
        InstrumenterConstructor.propagatingFromUpstream(requireNonNull(getter, "getter")),
        SpanKindExtractor.alwaysServer());
  }

  /**
   * Returns a new {@link Instrumenter} which will create {@linkplain SpanKind#PRODUCER producer}
   * spans and inject context into requests with the passed {@link TextMapSetter}.
   */
  public Instrumenter<REQUEST, RESPONSE> buildProducerInstrumenter(TextMapSetter<REQUEST> setter) {
    return buildInstrumenter(
        InstrumenterConstructor.propagatingToDownstream(requireNonNull(setter, "setter")),
        SpanKindExtractor.alwaysProducer());
  }

  /**
   * Returns a new {@link Instrumenter} which will create {@linkplain SpanKind#CONSUMER consumer}
   * spans and extract context from requests with the passed {@link TextMapGetter}.
   */
  public Instrumenter<REQUEST, RESPONSE> buildConsumerInstrumenter(TextMapGetter<REQUEST> getter) {
    return buildInstrumenter(
        InstrumenterConstructor.propagatingFromUpstream(requireNonNull(getter, "getter")),
        SpanKindExtractor.alwaysConsumer());
  }

  /**
   * Returns a new {@link Instrumenter} which will create spans with kind determined by the passed
   * {@link SpanKindExtractor} and extract context from requests with the passed {@link
   * TextMapGetter}.
   */
  // TODO: candidate for public API
  Instrumenter<REQUEST, RESPONSE> buildUpstreamInstrumenter(
      TextMapGetter<REQUEST> getter, SpanKindExtractor<REQUEST> spanKindExtractor) {
    return buildInstrumenter(
        InstrumenterConstructor.propagatingFromUpstream(requireNonNull(getter, "getter")),
        spanKindExtractor);
  }

  /**
   * Returns a new {@link Instrumenter} which will create spans with kind determined by the passed
   * {@link SpanKindExtractor} and inject context into requests with the passed {@link
   * TextMapSetter}.
   */
  // TODO: candidate for public API
  Instrumenter<REQUEST, RESPONSE> buildDownstreamInstrumenter(
      TextMapSetter<REQUEST> setter, SpanKindExtractor<REQUEST> spanKindExtractor) {
    return buildInstrumenter(
        InstrumenterConstructor.propagatingToDownstream(requireNonNull(setter, "setter")),
        spanKindExtractor);
  }

  /**
   * Returns a new {@link Instrumenter} which will create {@linkplain SpanKind#INTERNAL internal}
   * spans and do no context propagation.
   */
  public Instrumenter<REQUEST, RESPONSE> buildInstrumenter() {
    return buildInstrumenter(
        InstrumenterConstructor.internal(), SpanKindExtractor.alwaysInternal());
  }

  /**
   * Returns a new {@link Instrumenter} which will create spans with kind determined by the passed
   * {@link SpanKindExtractor} and do no context propagation.
   */
  public Instrumenter<REQUEST, RESPONSE> buildInstrumenter(
      SpanKindExtractor<? super REQUEST> spanKindExtractor) {
    return buildInstrumenter(
        InstrumenterConstructor.internal(), requireNonNull(spanKindExtractor, "spanKindExtractor"));
  }

  private Instrumenter<REQUEST, RESPONSE> buildInstrumenter(
      InstrumenterConstructor<REQUEST, RESPONSE> constructor,
      SpanKindExtractor<? super REQUEST> spanKindExtractor) {
    this.spanKindExtractor = spanKindExtractor;
    return constructor.create(this);
  }

  Tracer buildTracer() {
    TracerBuilder tracerBuilder =
        openTelemetry.getTracerProvider().tracerBuilder(instrumentationName);
    if (instrumentationVersion != null) {
      tracerBuilder.setInstrumentationVersion(instrumentationVersion);
    }
    String schemaUrl = getSchemaUrl();
    if (schemaUrl != null) {
      tracerBuilder.setSchemaUrl(schemaUrl);
    }
    return tracerBuilder.build();
  }

  List<OperationListener> buildOperationListeners() {
    // just copy the listeners list if there are no metrics registered
    if (operationMetrics.isEmpty()) {
      return new ArrayList<>(operationListeners);
    }

    List<OperationListener> listeners =
        new ArrayList<>(operationListeners.size() + operationMetrics.size());
    listeners.addAll(operationListeners);

    MeterBuilder meterBuilder = openTelemetry.getMeterProvider().meterBuilder(instrumentationName);
    if (instrumentationVersion != null) {
      meterBuilder.setInstrumentationVersion(instrumentationVersion);
    }
    String schemaUrl = getSchemaUrl();
    if (schemaUrl != null) {
      meterBuilder.setSchemaUrl(schemaUrl);
    }
    Meter meter = meterBuilder.build();
    for (OperationMetrics factory : operationMetrics) {
      listeners.add(factory.create(meter));
    }

    return listeners;
  }

  @Nullable
  private String getSchemaUrl() {
    // url set explicitly overrides url computed using attributes extractors
    if (schemaUrl != null) {
      return schemaUrl;
    }
    Set<String> computedSchemaUrls =
        attributesExtractors.stream()
            .filter(SchemaUrlProvider.class::isInstance)
            .map(SchemaUrlProvider.class::cast)
            .flatMap(
                provider -> {
                  String url = provider.internalGetSchemaUrl();
                  return url == null ? Stream.of() : Stream.of(url);
                })
            .collect(Collectors.toSet());
    switch (computedSchemaUrls.size()) {
      case 0:
        return null;
      case 1:
        return computedSchemaUrls.iterator().next();
      default:
        logger.log(
            WARNING,
            "Multiple schemaUrls were detected: {0}. The built Instrumenter will have no schemaUrl assigned.",
            computedSchemaUrls);
        return null;
    }
  }

  SpanSuppressor buildSpanSuppressor() {
    return new SpanSuppressors.ByContextKey(
        spanSuppressionStrategy.create(getSpanKeysFromAttributesExtractors()));
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

  private void propagateOperationListenersToOnEnd() {
    propagateOperationListenersToOnEnd = true;
  }

  private interface InstrumenterConstructor<RQ, RS> {
    Instrumenter<RQ, RS> create(InstrumenterBuilder<RQ, RS> builder);

    static <RQ, RS> InstrumenterConstructor<RQ, RS> internal() {
      return Instrumenter::new;
    }

    static <RQ, RS> InstrumenterConstructor<RQ, RS> propagatingToDownstream(
        TextMapSetter<RQ> setter) {
      return builder -> new PropagatingToDownstreamInstrumenter<>(builder, setter);
    }

    static <RQ, RS> InstrumenterConstructor<RQ, RS> propagatingFromUpstream(
        TextMapGetter<RQ> getter) {
      return builder -> new PropagatingFromUpstreamInstrumenter<>(builder, getter);
    }
  }

  static {
    InstrumenterUtil.setInstrumenterBuilderAccess(
        new InstrumenterBuilderAccess() {
          @Override
          public <RQ, RS> Instrumenter<RQ, RS> buildUpstreamInstrumenter(
              InstrumenterBuilder<RQ, RS> builder,
              TextMapGetter<RQ> getter,
              SpanKindExtractor<RQ> spanKindExtractor) {
            return builder.buildUpstreamInstrumenter(getter, spanKindExtractor);
          }

          @Override
          public <RQ, RS> Instrumenter<RQ, RS> buildDownstreamInstrumenter(
              InstrumenterBuilder<RQ, RS> builder,
              TextMapSetter<RQ> setter,
              SpanKindExtractor<RQ> spanKindExtractor) {
            return builder.buildDownstreamInstrumenter(setter, spanKindExtractor);
          }

          @Override
          public <RQ, RS> void propagateOperationListenersToOnEnd(
              InstrumenterBuilder<RQ, RS> builder) {
            builder.propagateOperationListenersToOnEnd();
          }
        });
  }
}
