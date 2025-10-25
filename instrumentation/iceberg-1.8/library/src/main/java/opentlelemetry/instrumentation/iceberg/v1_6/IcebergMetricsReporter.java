/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package opentlelemetry.instrumentation.iceberg.v1_6;

import org.apache.iceberg.metrics.CommitReport;
import org.apache.iceberg.metrics.CounterResult;
import org.apache.iceberg.metrics.MetricsReport;
import org.apache.iceberg.metrics.MetricsReporter;
import org.apache.iceberg.metrics.ScanMetricsResult;
import org.apache.iceberg.metrics.ScanReport;
import org.apache.iceberg.metrics.TimerResult;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.DoubleGauge;

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
    TimerResult duration = metrics.totalPlanningDuration();

    if (duration != null) {
      DoubleGauge metric =
          ScanMetricsBuilder.totalPlanningDuration(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.set(duration.totalDuration().toMillis());
    }

    CounterResult current = metrics.resultDataFiles();

    if (current != null) {
      DoubleGauge metric =
          ScanMetricsBuilder.scannedDataFilesCount(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.set(current.value());
    }

    current = metrics.resultDeleteFiles();

    if (current != null) {
      DoubleGauge metric =
          ScanMetricsBuilder.scannedDeleteFilesCount(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.set(current.value());
    }

    current = metrics.scannedDataManifests();

    if (current != null) {
      DoubleGauge metric =
          ScanMetricsBuilder.scannedDataManifestsCount(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.set(current.value());
    }

    current = metrics.scannedDeleteManifests();

    if (current != null) {
      DoubleGauge metric =
          ScanMetricsBuilder.scannedDeleteManifestsCount(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.set(current.value());
    }

    current = metrics.totalDataManifests();

    if (current != null) {
      DoubleGauge metric =
          ScanMetricsBuilder.totalDataManifestsCount(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.set(current.value());
    }

    current = metrics.totalDeleteManifests();

    if (current != null) {
      DoubleGauge metric =
          ScanMetricsBuilder.totalDeleteManifestsCount(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.set(current.value());
    }

    current = metrics.totalFileSizeInBytes();

    if (current != null) {
      DoubleGauge metric =
          ScanMetricsBuilder.scannedDataFilesSize(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.set(current.value());
    }

    current = metrics.totalDeleteFileSizeInBytes();

    if (current != null) {
      DoubleGauge metric =
          ScanMetricsBuilder.scannedDeleteFilesSize(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.set(current.value());
    }

    current = metrics.skippedDataManifests();

    if (current != null) {
      DoubleGauge metric =
          ScanMetricsBuilder.skippedDataManifests(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.set(current.value());
    }

    current = metrics.skippedDeleteManifests();

    if (current != null) {
      DoubleGauge metric =
          ScanMetricsBuilder.skippedDeleteManifests(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.set(current.value());
    }

    current = metrics.skippedDataFiles();

    if (current != null) {
      DoubleGauge metric =
          ScanMetricsBuilder.skippedDataFiles(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.set(current.value());
    }

    current = metrics.skippedDeleteFiles();

    if (current != null) {
      DoubleGauge metric =
          ScanMetricsBuilder.skippedDeleteFiles(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.set(current.value());
    }

    current = metrics.indexedDeleteFiles();

    if (current != null) {
      DoubleGauge metric =
          ScanMetricsBuilder.indexedDeleteFiles(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.set(current.value());
    }

    current = metrics.equalityDeleteFiles();

    if (current != null) {
      DoubleGauge metric =
          ScanMetricsBuilder.equalityDeleteFiles(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.set(current.value());
    }

    current = metrics.positionalDeleteFiles();

    if (current != null) {
      DoubleGauge metric =
          ScanMetricsBuilder.positionDeleteFiles(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.set(current.value());
    }

    current = metrics.dvs();

    if (current != null) {
      DoubleGauge metric =
          ScanMetricsBuilder.deletionVectorFiles(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.set(current.value());
    }

  }

  void reportCommitMetrics(CommitReport commitReport) {}
}
