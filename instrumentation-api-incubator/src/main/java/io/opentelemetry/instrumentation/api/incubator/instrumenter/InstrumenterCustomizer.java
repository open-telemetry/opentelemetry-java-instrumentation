/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractor;
import java.util.function.UnaryOperator;

/**
 * Provides customizations for instrumentation, including operation metrics, attributes extraction,
 * and context customization.
 *
 * <p>This class is passed to {@link
 * InstrumenterCustomizerProvider#customize(InstrumenterCustomizer)} to allow external modules or
 * plugins to contribute custom logic for specific instrumented libraries, without modifying core
 * instrumentation code. This class is internal and is hence not for public use. Its APIs are
 * unstable and can change at any time.
 */
public interface InstrumenterCustomizer {

  /**
   * Returns the name of the instrumentation that this customizer applies to.
   *
   * @return the name of the instrumentation this customizer targets
   */
  String getInstrumentationName();

  /**
   * Tests whether given instrumenter produces telemetry of specified type. Instrumentation type is
   * detected based on the standard {@link AttributesExtractor} implementations used by this
   * instrumenter e.g. instrumenters that use {@link HttpClientAttributesExtractor} have {@link
   * InstrumentationType#HTTP_CLIENT} type.
   *
   * @return the name of the instrumentation this customizer targets
   */
  boolean hasType(InstrumentationType type);

  /**
   * Adds a single {@link AttributesExtractor} to the instrumenter. This extractor will be used to
   * extract attributes from requests and responses during the request lifecycle.
   *
   * @param extractor the attributes extractor to add
   * @return this InstrumenterCustomizer for method chaining
   */
  InstrumenterCustomizer addAttributesExtractor(AttributesExtractor<?, ?> extractor);

  /**
   * Adds multiple {@link AttributesExtractor}s to the instrumenter. These extractors will be used
   * to extract attributes from requests and responses during the request lifecycle.
   *
   * @param extractors the collection of attributes extractors to add
   * @return this InstrumenterCustomizer for method chaining
   */
  InstrumenterCustomizer addAttributesExtractors(
      Iterable<? extends AttributesExtractor<?, ?>> extractors);

  /**
   * Adds an {@link OperationMetrics} implementation to the instrumenter. This will be used to
   * create metrics for the instrumented operations.
   *
   * @param operationMetrics the metrics factory to add
   * @return this InstrumenterCustomizer for method chaining
   */
  InstrumenterCustomizer addOperationMetrics(OperationMetrics operationMetrics);

  /**
   * Adds a {@link ContextCustomizer} that will customize the context during {@link
   * Instrumenter#start(Context, Object)}.
   *
   * @param customizer the context customizer to add
   * @return this InstrumenterCustomizer for method chaining
   */
  InstrumenterCustomizer addContextCustomizer(ContextCustomizer<?> customizer);

  /**
   * Sets a transformer function that will modify the {@link SpanNameExtractor}. This allows
   * customizing how span names are generated for the instrumented operations.
   *
   * @param spanNameExtractor function that transforms the original span name extractor
   * @return this InstrumenterCustomizer for method chaining
   * @deprecated Use {@link #setSpanNameExtractorCustomizer(UnaryOperator)} instead.
   */
  @Deprecated
  default InstrumenterCustomizer setSpanNameExtractor(
      UnaryOperator<SpanNameExtractor<?>> spanNameExtractor) {
    return setSpanNameExtractorCustomizer(spanNameExtractor);
  }

  /**
   * Sets a transformer function that will modify the {@link SpanNameExtractor}. This allows
   * customizing how span names are generated for the instrumented operations.
   *
   * @param spanNameExtractorCustomizer function that transforms the original span name extractor
   * @return this InstrumenterCustomizer for method chaining
   */
  InstrumenterCustomizer setSpanNameExtractorCustomizer(
      UnaryOperator<SpanNameExtractor<?>> spanNameExtractorCustomizer);

  /**
   * Sets a transformer function that will modify the {@link SpanStatusExtractor}. This allows
   * customizing how span statuses are generated for the instrumented operations.
   *
   * @param spanStatusExtractor function that transforms the original span status extractor
   * @return this InstrumenterCustomizer for method chaining
   * @deprecated Use {@link #setSpanStatusExtractorCustomizer(UnaryOperator)} instead.
   */
  @Deprecated
  default InstrumenterCustomizer setSpanStatusExtractor(
      UnaryOperator<SpanStatusExtractor<?, ?>> spanStatusExtractor) {
    return setSpanStatusExtractorCustomizer(spanStatusExtractor);
  }

  /**
   * Sets a transformer function that will modify the {@link SpanStatusExtractor}. This allows
   * customizing how span statuses are generated for the instrumented operations.
   *
   * @param spanStatusExtractorCustomizer function that transforms the original span status extractor
   * @return this InstrumenterCustomizer for method chaining
   */
  InstrumenterCustomizer setSpanStatusExtractorCustomizer(
      UnaryOperator<SpanStatusExtractor<?, ?>> spanStatusExtractorCustomizer);

  /** Types of instrumentation. */
  enum InstrumentationType {
    HTTP_CLIENT,
    HTTP_SERVER,
    DB_CLIENT,
    RPC_CLIENT,
    RPC_SERVER,
    MESSAGING_PRODUCER,
    MESSAGING_CONSUMER_RECEIVE,
    MESSAGING_CONSUMER_PROCESS
  }
}
