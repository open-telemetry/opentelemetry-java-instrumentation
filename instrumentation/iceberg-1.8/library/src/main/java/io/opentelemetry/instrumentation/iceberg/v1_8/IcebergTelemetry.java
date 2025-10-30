/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.iceberg.v1_8;

import io.opentelemetry.api.OpenTelemetry;
import org.apache.iceberg.TableScan;

public class IcebergTelemetry {
  private final OpenTelemetry openTelemetry;

  public static IcebergTelemetry create(OpenTelemetry openTelemetry) {
    return new IcebergTelemetry(openTelemetry);
  }

  IcebergTelemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  public TableScan wrapTableScan(TableScan tableScan) {
    return tableScan.metricsReporter(new IcebergMetricsReporter(openTelemetry));
  }
}
