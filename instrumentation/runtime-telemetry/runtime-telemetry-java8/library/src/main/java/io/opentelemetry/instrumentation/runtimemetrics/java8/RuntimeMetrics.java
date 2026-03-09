/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.runtimetelemetry.RuntimeTelemetry;

/**
 * The entry point class for runtime metrics support using JMX.
 *
 * @deprecated Use {@link RuntimeTelemetry} in the {@code runtime-telemetry} module instead.
 */
@Deprecated
public final class RuntimeMetrics implements AutoCloseable {

  private final RuntimeTelemetry delegate;

  RuntimeMetrics(RuntimeTelemetry delegate) {
    this.delegate = delegate;
  }

  /**
   * Create and start {@link RuntimeMetrics}.
   *
   * <p>Listens for select JMX beans, extracts data, and records to various metrics. Recording will
   * continue until {@link #close()} is called.
   *
   * @param openTelemetry the {@link OpenTelemetry} instance used to record telemetry
   * @deprecated Use {@link RuntimeTelemetry#create(OpenTelemetry)} in the {@code runtime-telemetry}
   *     module instead.
   */
  @Deprecated
  public static RuntimeMetrics create(OpenTelemetry openTelemetry) {
    return new RuntimeMetricsBuilder(openTelemetry).build();
  }

  /**
   * Create a builder for configuring {@link RuntimeMetrics}.
   *
   * @param openTelemetry the {@link OpenTelemetry} instance used to record telemetry
   * @deprecated Use {@link RuntimeTelemetry#builder(OpenTelemetry)} in the {@code
   *     runtime-telemetry} module instead.
   */
  @Deprecated
  public static RuntimeMetricsBuilder builder(OpenTelemetry openTelemetry) {
    return new RuntimeMetricsBuilder(openTelemetry);
  }

  /** Stop recording JMX metrics. */
  @Override
  public void close() {
    delegate.close();
  }
}
