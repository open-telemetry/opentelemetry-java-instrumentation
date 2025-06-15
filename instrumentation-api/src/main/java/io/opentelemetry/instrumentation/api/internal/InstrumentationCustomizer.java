/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * A service provider interface (SPI) for providing customizations for instrumentation, including
 * operation metrics, attributes extraction, and context customization.
 *
 * <p>This allows external modules or plugins to contribute custom logic for specific instrumented
 * libraries, without modifying core instrumentation code. This class is internal and is hence not
 * for public use. Its APIs are unstable and can change at any time.
 */
public interface InstrumentationCustomizer {

  /**
   * Returns a predicate that determines whether this customizer supports a given instrumentation
   * name.
   *
   * <p>The customizer will only be applied if the current instrumentation name matches the
   * predicate. For example, the predicate might match names like "io.opentelemetry.netty-3.8" or
   * "io.opentelemetry.apache-httpclient-4.3".
   *
   * @return a predicate for supported instrumentation names
   */
  Predicate<String> instrumentationNamePredicate();

  /**
   * Returns a new instance of an {@link OperationMetrics} that will record metrics for the
   * instrumented operation.
   *
   * @return an operation metrics instance, or null if not applicable
   */
  default OperationMetrics getOperationMetrics() {
    return null;
  }

  /**
   * Returns a new instance of an {@link AttributesExtractor} that will extract attributes from
   * requests and responses during the instrumentation process.
   *
   * @param <REQUEST> the type of request object used by the instrumented library
   * @param <RESPONSE> the type of response object used by the instrumented library
   * @return an attributes extractor instance, or null if not applicable
   */
  default <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> getAttributesExtractor() {
    return null;
  }

  /**
   * Returns a list of {@link AttributesExtractor}s that will extract attributes from requests and
   * responses during the instrumentation process.
   *
   * <p>This allows providing multiple extractors for a single instrumentation. The default
   * implementation returns {@code null} for backward compatibility.
   *
   * @param <REQUEST> the type of request object used by the instrumented library
   * @param <RESPONSE> the type of response object used by the instrumented library
   * @return a list of attributes extractors, or null if not applicable
   */
  @Nullable
  default <REQUEST, RESPONSE>
      List<AttributesExtractor<REQUEST, RESPONSE>> getAttributesExtractors() {
    return null;
  }

  /**
   * Returns a new instance of a {@link ContextCustomizer} that will customize the tracing context
   * during request processing.
   *
   * @param <REQUEST> the type of request object used by the instrumented library
   * @return a context customizer instance, or null if not applicable
   */
  default <REQUEST> ContextCustomizer<REQUEST> getContextCustomizer() {
    return null;
  }

  /**
   * Returns a new instance of a {@link SpanNameExtractor} that will customize the span name during
   * request processing.
   *
   * @param <REQUEST> the type of request object used by the instrumented library
   * @return a customized {@link SpanNameExtractor}, or null if not applicable
   */
  default <REQUEST> SpanNameExtractor<REQUEST> getSpanNameExtractor() {
    return null;
  }
}
