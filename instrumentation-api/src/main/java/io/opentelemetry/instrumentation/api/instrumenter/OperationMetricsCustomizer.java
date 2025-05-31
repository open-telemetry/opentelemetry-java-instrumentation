/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import java.util.List;

/**
 * A service provider interface (SPI) for providing custom {@link OperationMetrics} instances that
 * are conditionally applied based on the instrumentation name.
 *
 * <p>This allows external modules or plugins to contribute custom metrics collection logic for
 * specific instrumented operations, without modifying core instrumentation code.
 */
public interface OperationMetricsCustomizer {

  /**
   * Returns a list of instrumentation names that this metrics customizer supports.
   *
   * <p>The customizer will only be applied if the current instrumentation matches one of the
   * returned names. For example: ["io.opentelemetry.spring-webmvc-5.0",
   * "io.opentelemetry.netty-3.8"].
   *
   * @return a list of supported instrumentation names
   */
  List<String> instrumentationNames();

  /**
   * Returns a new instance of an {@link OperationMetrics} that will record metrics for the
   * instrumented operation.
   *
   * @return an operation metrics instance
   */
  OperationMetrics get();
}
