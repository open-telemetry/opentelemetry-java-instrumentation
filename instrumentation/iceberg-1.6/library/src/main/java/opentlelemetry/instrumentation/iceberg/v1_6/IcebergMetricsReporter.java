/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package opentlelemetry.instrumentation.iceberg.v1_6;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.DoubleGauge;
import org.apache.iceberg.metrics.CommitReport;
import org.apache.iceberg.metrics.MetricsReport;
import org.apache.iceberg.metrics.MetricsReporter;
import org.apache.iceberg.metrics.ScanMetricsResult;
import org.apache.iceberg.metrics.ScanReport;

public class IcebergMetricsReporter implements MetricsReporter {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.iceberg_1.6";

  private final OpenTelemetry openTelemetry;

  IcebergMetricsReporter(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  @Override
  public void report(MetricsReport report) {
    if (report instanceof ScanReport) {
      reportScanMetrics((ScanReport) report);
    } else if (report instanceof CommitReport) {
      reportCommitMetrics((CommitReport) report);
    }
  }

  void reportScanMetrics(ScanReport scanReport) {
    final ScanMetricsResult metrics = scanReport.scanMetrics();

    if (metrics.indexedDeleteFiles() != null) {
      DoubleGauge metric =
          ScanMetricsBuilder.indexedDeleteFiles(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.set(metrics.indexedDeleteFiles().value());
    }
  }

  void reportCommitMetrics(CommitReport commitReport) {}
}
