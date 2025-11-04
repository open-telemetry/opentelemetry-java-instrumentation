/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.iceberg.v1_8;

import org.apache.iceberg.Scan;
import org.apache.iceberg.ScanTask;
import org.apache.iceberg.ScanTaskGroup;

import io.opentelemetry.api.OpenTelemetry;

public class IcebergTelemetry {
  private final OpenTelemetry openTelemetry;

  public static IcebergTelemetry create(OpenTelemetry openTelemetry) {
    return new IcebergTelemetry(openTelemetry);
  }

  IcebergTelemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  public <T1, T2 extends ScanTask, T3 extends ScanTaskGroup<T2>> T1 wrapScan(
      Scan<T1, T2, T3> scan) {
    return scan.metricsReporter(new IcebergMetricsReporter(openTelemetry));
  }
}
