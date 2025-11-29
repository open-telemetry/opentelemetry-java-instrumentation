/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.instrumenter.internal;

import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.ShouldStartFilter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.internal.InternalInstrumenterCustomizer;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class InstrumenterCustomizerImpl implements InstrumenterCustomizer {
  private static final Map<InstrumentationType, SpanKey> typeToSpanKey = new HashMap<>();

  static {
    typeToSpanKey.put(InstrumentationType.HTTP_CLIENT, SpanKey.HTTP_CLIENT);
    typeToSpanKey.put(InstrumentationType.HTTP_SERVER, SpanKey.HTTP_SERVER);
    typeToSpanKey.put(InstrumentationType.DB_CLIENT, SpanKey.DB_CLIENT);
    typeToSpanKey.put(InstrumentationType.RPC_CLIENT, SpanKey.RPC_CLIENT);
    typeToSpanKey.put(InstrumentationType.RPC_SERVER, SpanKey.RPC_SERVER);
    typeToSpanKey.put(InstrumentationType.MESSAGING_PRODUCER, SpanKey.PRODUCER);
    typeToSpanKey.put(InstrumentationType.MESSAGING_CONSUMER_RECEIVE, SpanKey.CONSUMER_RECEIVE);
    typeToSpanKey.put(InstrumentationType.MESSAGING_CONSUMER_PROCESS, SpanKey.CONSUMER_RECEIVE);
  }

  private final InternalInstrumenterCustomizer customizer;

  public InstrumenterCustomizerImpl(InternalInstrumenterCustomizer customizer) {
    this.customizer = customizer;
  }

  @Override
  public String getInstrumentationName() {
    return customizer.getInstrumentationName();
  }

  @Override
  public boolean hasType(InstrumentationType type) {
    SpanKey spanKey = typeToSpanKey.get(type);
    if (spanKey == null) {
      throw new IllegalArgumentException("unexpected instrumentation type: " + type);
    }
    return customizer.hasType(spanKey);
  }

  @Override
  public InstrumenterCustomizer addAttributesExtractor(AttributesExtractor<?, ?> extractor) {
    customizer.addAttributesExtractor(extractor);
    return this;
  }

  @Override
  public InstrumenterCustomizer addAttributesExtractors(
      Iterable<? extends AttributesExtractor<?, ?>> extractors) {
    customizer.addAttributesExtractors(extractors);
    return this;
  }

  @Override
  public InstrumenterCustomizer addOperationMetrics(OperationMetrics operationMetrics) {
    customizer.addOperationMetrics(operationMetrics);
    return this;
  }

  @Override
  public InstrumenterCustomizer addContextCustomizer(ContextCustomizer<?> customizer) {
    this.customizer.addContextCustomizer(customizer);
    return this;
  }

  @Override
  public InstrumenterCustomizer addShouldStartFilter(ShouldStartFilter<?> filter) {
    customizer.addShouldStartFilter(filter);
    return this;
  }

  @Override
  @SuppressWarnings("FunctionalInterfaceClash") // interface has deprecated overload
  public InstrumenterCustomizer setSpanNameExtractor(
      UnaryOperator<SpanNameExtractor<?>> spanNameExtractor) {
    customizer.setSpanNameExtractor(spanNameExtractor);
    return this;
  }

  @Override
  public InstrumenterCustomizer setSpanStatusExtractor(
      UnaryOperator<SpanStatusExtractor<?, ?>> spanStatusExtractor) {
    customizer.setSpanStatusExtractor(spanStatusExtractor);
    return this;
  }
}
