/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.runtimetelemetry.RuntimeTelemetry;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

/**
 * The entry point class for runtime metrics support using JFR and JMX.
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
   * Create and start {@link RuntimeMetrics}, configured with the default {@link JfrFeature}s.
   *
   * <p>Listens for select JFR events, extracts data, and records to various metrics. Recording will
   * continue until {@link #close()} is called.
   *
   * @param openTelemetry the {@link OpenTelemetry} instance used to record telemetry
   */
  public static RuntimeMetrics create(OpenTelemetry openTelemetry) {
    return new RuntimeMetricsBuilder(openTelemetry).build();
  }

  /**
   * Create a builder for configuring {@link RuntimeMetrics}.
   *
   * @param openTelemetry the {@link OpenTelemetry} instance used to record telemetry
   */
  public static RuntimeMetricsBuilder builder(OpenTelemetry openTelemetry) {
    return new RuntimeMetricsBuilder(openTelemetry);
  }

  // Only used by tests
  @Nullable
  Object getJfrRuntimeMetrics() {
    try {
      Method method = RuntimeTelemetry.class.getDeclaredMethod("getJfrTelemetry");
      method.setAccessible(true);
      return method.invoke(delegate);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to access JFR telemetry via reflection", e);
    }
  }

  /** Stop recording JFR events. */
  @Override
  public void close() {
    delegate.close();
  }
}
