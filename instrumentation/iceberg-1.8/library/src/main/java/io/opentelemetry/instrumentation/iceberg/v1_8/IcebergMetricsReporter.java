/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.iceberg.v1_8;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongGauge;
import org.apache.iceberg.metrics.CommitReport;
import org.apache.iceberg.metrics.CounterResult;
import org.apache.iceberg.metrics.MetricsReport;
import org.apache.iceberg.metrics.MetricsReporter;
import org.apache.iceberg.metrics.ScanMetricsResult;
import org.apache.iceberg.metrics.ScanReport;
import org.apache.iceberg.metrics.TimerResult;

public class IcebergMetricsReporter implements MetricsReporter {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.iceberg_1.8";

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
    ScanMetricsResult metrics = scanReport.scanMetrics();
    TimerResult duration = metrics.totalPlanningDuration();

    if (duration != null) {
      LongGauge metric =
          ScanMetricsBuilder.totalPlanningDuration(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.set(duration.totalDuration().toMillis());
    }

    CounterResult current = metrics.resultDataFiles();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.scannedDataFilesCount(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value());
    }

    current = metrics.resultDeleteFiles();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.scannedDeleteFilesCount(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value());
    }

    current = metrics.scannedDataManifests();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.scannedDataManifestsCount(
              openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value());
    }

    current = metrics.scannedDeleteManifests();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.scannedDeleteManifestsCount(
              openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value());
    }

    current = metrics.totalDataManifests();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.totalDataManifestsCount(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value());
    }

    current = metrics.totalDeleteManifests();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.totalDeleteManifestsCount(
              openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value());
    }

    current = metrics.totalFileSizeInBytes();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.scannedDataFilesSize(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value());
    }

    current = metrics.totalDeleteFileSizeInBytes();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.scannedDeleteFilesSize(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value());
    }

    current = metrics.skippedDataManifests();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.skippedDataManifestsCount(
              openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value());
    }

    current = metrics.skippedDeleteManifests();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.skippedDeleteManifestsCount(
              openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value());
    }

    current = metrics.skippedDataFiles();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.skippedDataFilesCount(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value());
    }

    current = metrics.skippedDeleteFiles();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.skippedDeleteFilesCount(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value());
    }

    current = metrics.indexedDeleteFiles();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.indexedDeleteFilesCount(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value());
    }

    current = metrics.equalityDeleteFiles();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.equalityDeleteFilesCount(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value());
    }

    current = metrics.positionalDeleteFiles();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.positionDeleteFilesCount(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value());
    }

    current = metrics.dvs();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.deletionVectorFilesCount(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value());
    }
  }

  void reportCommitMetrics(CommitReport commitReport) {}
}
