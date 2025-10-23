/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.util.function.UnaryOperator;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public interface InternalInstrumenterCustomizer<REQUEST, RESPONSE> {

  String getInstrumentationName();

  void addAttributesExtractor(AttributesExtractor<REQUEST, RESPONSE> extractor);

  void addAttributesExtractors(
      Iterable<? extends AttributesExtractor<REQUEST, RESPONSE>> extractors);

  void addOperationMetrics(OperationMetrics operationMetrics);

  void addContextCustomizer(ContextCustomizer<REQUEST> customizer);

  void setSpanNameExtractor(
      UnaryOperator<SpanNameExtractor<? super REQUEST>> spanNameExtractorTransformer);
}
