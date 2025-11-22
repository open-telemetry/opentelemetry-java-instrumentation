/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.iceberg.v1_8;

import org.apache.iceberg.Scan;
import org.apache.iceberg.ScanTask;
import org.apache.iceberg.ScanTaskGroup;

import io.opentelemetry.api.OpenTelemetry;

/** Entrypoint for instrumenting Apache Iceberg scan metrics */
public final class IcebergTelemetry {
  private final OpenTelemetry openTelemetry;

  /** Returns a new {@link IcebergTelemetry} configured with the given {@link OpenTelemetry}. */
  public static IcebergTelemetry create(OpenTelemetry openTelemetry) {
    return new IcebergTelemetry(openTelemetry);
  }

  IcebergTelemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Creates a new {@link Scan} instance based on an existing {@link Scan} instance. The new instance is associated
   * with a custom {@link org.apache.iceberg.metrics.MetricsReporter} that reports scan metrics using the configured {@link OpenTelemetry} instance.
   * @param <T1> the child class, returned by method chaining, e.g., {@link Scan#project(org.apache.iceberg.Schema)}
   * @param <T2> the type of tasks produces by this scan
   * @param <T3> the type of task groups produces by this scan
   * @param scan the original scan instance that will be instrumented
   * @return an instrumented {@link Scan} instance based on the provided instance
   */
  public <T1, T2 extends ScanTask, T3 extends ScanTaskGroup<T2>> T1 wrapScan(
      Scan<T1, T2, T3> scan) {
    return scan.metricsReporter(new IcebergMetricsReporter(openTelemetry));
  }
}
