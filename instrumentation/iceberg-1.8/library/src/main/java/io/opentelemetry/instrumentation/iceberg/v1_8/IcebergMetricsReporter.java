/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.iceberg.v1_8;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import org.apache.iceberg.metrics.CounterResult;
import org.apache.iceberg.metrics.MetricsReport;
import org.apache.iceberg.metrics.MetricsReporter;
import org.apache.iceberg.metrics.ScanMetricsResult;
import org.apache.iceberg.metrics.ScanReport;
import org.apache.iceberg.metrics.TimerResult;

final class IcebergMetricsReporter implements MetricsReporter {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.iceberg-1.8";
  private static final AttributeKey<Long> SCHEMA_ID = AttributeKey.longKey("iceberg.schema.id");
  private static final AttributeKey<String> TABLE_NAME =
      AttributeKey.stringKey("iceberg.table.name");
  private static final AttributeKey<Long> SNAPHSOT_ID = AttributeKey.longKey("iceberg.snapshot.id");

  private final OpenTelemetry openTelemetry;

  IcebergMetricsReporter(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  @Override
  public void report(MetricsReport report) {
    if (report instanceof ScanReport) {
      reportScanMetrics((ScanReport) report);
    }
  }

  void reportScanMetrics(ScanReport scanReport) {
    Attributes scanAttributes =
        Attributes.of(
            SCHEMA_ID,
            Long.valueOf(scanReport.schemaId()),
            TABLE_NAME,
            scanReport.tableName(),
            SNAPHSOT_ID,
            scanReport.snapshotId());
    ScanMetricsResult metrics = scanReport.scanMetrics();
    TimerResult duration = metrics.totalPlanningDuration();

    if (duration != null) {
      DoubleHistogram metric =
          ScanMetricsBuilder.totalPlanningDuration(
              openTelemetry.getMeter(INSTRUMENTATION_NAME), "s");
      metric.record(duration.totalDuration().toMillis() / 1000.0, scanAttributes);
    }

    CounterResult current = metrics.resultDataFiles();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.scannedDataFilesCount(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), scanAttributes);
    }

    current = metrics.resultDeleteFiles();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.scannedDeleteFilesCount(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), scanAttributes);
    }

    current = metrics.scannedDataManifests();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.scannedDataManifestsCount(
              openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), scanAttributes);
    }

    current = metrics.scannedDeleteManifests();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.scannedDeleteManifestsCount(
              openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), scanAttributes);
    }

    current = metrics.totalDataManifests();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.totalDataManifestsCount(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), scanAttributes);
    }

    current = metrics.totalDeleteManifests();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.totalDeleteManifestsCount(
              openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), scanAttributes);
    }

    current = metrics.totalFileSizeInBytes();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.scannedDataFilesSize(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), scanAttributes);
    }

    current = metrics.totalDeleteFileSizeInBytes();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.scannedDeleteFilesSize(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), scanAttributes);
    }

    current = metrics.skippedDataManifests();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.skippedDataManifestsCount(
              openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), scanAttributes);
    }

    current = metrics.skippedDeleteManifests();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.skippedDeleteManifestsCount(
              openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), scanAttributes);
    }

    current = metrics.skippedDataFiles();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.skippedDataFilesCount(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), scanAttributes);
    }

    current = metrics.skippedDeleteFiles();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.skippedDeleteFilesCount(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), scanAttributes);
    }

    current = metrics.indexedDeleteFiles();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.indexedDeleteFilesCount(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), scanAttributes);
    }

    current = metrics.equalityDeleteFiles();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.equalityDeleteFilesCount(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), scanAttributes);
    }

    current = metrics.positionalDeleteFiles();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.positionDeleteFilesCount(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), scanAttributes);
    }

    current = metrics.dvs();

    if (current != null) {
      LongCounter metric =
          ScanMetricsBuilder.deletionVectorFilesCount(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), scanAttributes);
    }
  }
}
