/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.iceberg.v1_8;

import io.opentelemetry.api.OpenTelemetry;
import org.apache.iceberg.Scan;
import org.apache.iceberg.ScanTask;
import org.apache.iceberg.ScanTaskGroup;

public class IcebergTelemetry {
  private final OpenTelemetry openTelemetry;

  public static IcebergTelemetry create(OpenTelemetry openTelemetry) {
    return new IcebergTelemetry(openTelemetry);
  }

  IcebergTelemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  public <ThisT, T extends ScanTask, G extends ScanTaskGroup<T>> ThisT wrapScan(
      Scan<ThisT, T, G> scan) {
    return scan.metricsReporter(new IcebergMetricsReporter(openTelemetry));
  }
}
