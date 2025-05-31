/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import java.util.List;

/**
 * A service provider interface (SPI) for providing {@link AttributesExtractorCustomizer} instances
 * that are conditionally applied based on the instrumentation name.
 *
 * <p>This allows external modules or plugins to contribute custom attribute extraction logic for
 * specific instrumented libraries, without modifying core instrumentation code.
 */
public interface AttributesExtractorCustomizer {
  /**
   * Returns a list of instrumentation names that this customizer supports.
   *
   * <p>The customizer will only be applied if the current instrumentation matches one of the
   * returned names. For example: ["io.opentelemetry.netty-3.8",
   * "io.opentelemetry.apache-httpclient-4.3"].
   *
   * @return a list of supported instrumentation names
   */
  List<String> instrumentationNames();

  /**
   * Returns a new instance of an {@link AttributesExtractor} that will extract attributes from
   * requests and responses during the instrumentation process.
   *
   * @param <REQUEST> the type of request object used by the instrumented library
   * @param <RESPONSE> the type of response object used by the instrumented library
   * @return an attributes extractor instance
   */
  <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> get();
}
