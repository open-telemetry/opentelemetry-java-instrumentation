/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.util.function.Function;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public abstract class InstrumenterCustomizerImpl implements InstrumenterCustomizer {

  private final InstrumenterBuilder<?, ?> builder;

  public InstrumenterCustomizerImpl(InstrumenterBuilder<?, ?> builder) {
    this.builder = builder;
  }

  @Override
  @SuppressWarnings("unchecked")
  public InstrumenterCustomizer addAttributesExtractor(AttributesExtractor<?, ?> extractor) {
    builder.addAttributesExtractor((AttributesExtractor<Object, Object>) extractor);
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public InstrumenterCustomizer addAttributesExtractors(
      Iterable<? extends AttributesExtractor<?, ?>> extractors) {
    builder.addAttributesExtractors((Iterable<AttributesExtractor<Object, Object>>) extractors);
    return this;
  }

  @Override
  public InstrumenterCustomizer addOperationMetrics(OperationMetrics operationMetrics) {
    builder.addOperationMetrics(operationMetrics);
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public InstrumenterCustomizer addContextCustomizer(ContextCustomizer<?> customizer) {
    builder.addContextCustomizer((ContextCustomizer<Object>) customizer);
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public InstrumenterCustomizer setSpanNameExtractor(
      Function<SpanNameExtractor<?>, SpanNameExtractor<?>> spanNameExtractorTransformer) {
    builder.spanNameExtractor =
        (SpanNameExtractor<? super Object>)
            spanNameExtractorTransformer.apply(builder.spanNameExtractor);
    return this;
  }
}
