/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.instrumenter;

import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.util.function.Function;

/**
 * A service provider interface (SPI) for providing customizations for instrumentation, including
 * operation metrics, attributes extraction, and context customization.
 *
 * <p>This allows external modules or plugins to contribute custom logic for specific instrumented
 * libraries, without modifying core instrumentation code. This class is internal and is hence not
 * for public use. Its APIs are unstable and can change at any time.
 */
public interface InstrumenterCustomizer {

  /**
   * Returns the name of the instrumentation that this customizer applies to. This allows for
   * efficient mapping of customizers to specific instrumentations rather than using predicates for
   * matching.
   *
   * @return the name of the instrumentation this customizer targets
   */
  String getInstrumentationName();

  /**
   * Adds a single AttributesExtractor to the instrumenter. This extractor will be used to extract
   * attributes from requests and responses during span creation and enrichment.
   *
   * @param extractor the attributes extractor to add
   * @return this InstrumenterCustomizer for method chaining
   */
  InstrumenterCustomizer addAttributesExtractor(AttributesExtractor<?, ?> extractor);

  /**
   * Adds multiple AttributesExtractors to the instrumenter. These extractors will be used to
   * extract attributes from requests and responses during span creation and enrichment.
   *
   * @param extractors the collection of attributes extractors to add
   * @return this InstrumenterCustomizer for method chaining
   */
  InstrumenterCustomizer addAttributesExtractors(
      Iterable<? extends AttributesExtractor<?, ?>> extractors);

  /**
   * Adds an OperationMetrics implementation to the instrumenter. This will be used to create
   * metrics capturing the request processing metrics for the instrumented operations.
   *
   * @param operationMetrics the metrics factory to add
   * @return this InstrumenterCustomizer for method chaining
   */
  InstrumenterCustomizer addOperationMetrics(OperationMetrics operationMetrics);

  /**
   * Sets a ContextCustomizer for the instrumenter. The customizer will modify the context during
   * the Instrumenter.start() operation, allowing custom context propagation or enrichment.
   *
   * @param customizer the context customizer to set
   * @return this InstrumenterCustomizer for method chaining
   */
  InstrumenterCustomizer addContextCustomizer(ContextCustomizer<?> customizer);

  /**
   * Sets a transformer function that will modify the SpanNameExtractor. This allows customizing how
   * span names are generated for the instrumented operations.
   *
   * @param spanNameExtractorTransformer function that transforms the original span name extractor
   * @return this InstrumenterCustomizer for method chaining
   */
  InstrumenterCustomizer setSpanNameExtractor(
      Function<SpanNameExtractor<?>, SpanNameExtractor<?>> spanNameExtractorTransformer);
}
