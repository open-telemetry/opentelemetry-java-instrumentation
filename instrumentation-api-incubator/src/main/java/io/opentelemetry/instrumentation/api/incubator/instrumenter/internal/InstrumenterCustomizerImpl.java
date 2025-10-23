/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.instrumenter.internal;

import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.InternalInstrumenterCustomizer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class InstrumenterCustomizerImpl implements InstrumenterCustomizer {
  private final InternalInstrumenterCustomizer customizer;

  public InstrumenterCustomizerImpl(InternalInstrumenterCustomizer customizer) {
    this.customizer = customizer;
  }

  @Override
  public String getInstrumentationName() {
    return customizer.getInstrumentationName();
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
  // Deprecated method kept for backward compatibility, delegates to UnaryOperator version
  @SuppressWarnings({"FunctionalInterfaceClash", "deprecation"})
  public InstrumenterCustomizer setSpanNameExtractor(
      Function<SpanNameExtractor<?>, SpanNameExtractor<?>> spanNameExtractorTransformer) {
    return setSpanNameExtractor(
        (UnaryOperator<SpanNameExtractor<?>>) spanNameExtractorTransformer::apply);
  }

  @Override
  public InstrumenterCustomizer setSpanNameExtractor(
      UnaryOperator<SpanNameExtractor<?>> spanNameExtractorTransformer) {
    customizer.setSpanNameExtractor(spanNameExtractorTransformer);
    return this;
  }
}
