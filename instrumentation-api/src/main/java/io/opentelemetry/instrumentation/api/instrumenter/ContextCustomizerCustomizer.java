/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import java.util.List;

/**
 * A service provider interface (SPI) for providing custom {@link ContextCustomizer} implementations
 * that are conditionally applied based on the instrumentation name.
 *
 * <p>This allows external modules or plugins to customize context propagation and initialization
 * logic for specific instrumented libraries, without modifying core instrumentation code.
 */
public interface ContextCustomizerCustomizer {

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
   * Returns a new instance of a {@link ContextCustomizer} that will customize the tracing context
   * during request processing.
   *
   * @param <REQUEST> the type of request object used by the instrumented library
   * @return a context customizer instance
   */
  <REQUEST> ContextCustomizer<REQUEST> get();
}
