/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.iceberg.v1_8;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongGauge;
import java.util.Locale;
import org.apache.iceberg.metrics.CommitMetricsResult;
import org.apache.iceberg.metrics.CommitReport;
import org.apache.iceberg.metrics.CounterResult;
import org.apache.iceberg.metrics.MetricsReport;
import org.apache.iceberg.metrics.MetricsReporter;
import org.apache.iceberg.metrics.ScanMetricsResult;
import org.apache.iceberg.metrics.ScanReport;
import org.apache.iceberg.metrics.TimerResult;

public class IcebergMetricsReporter implements MetricsReporter {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.iceberg_1.8";
  private static final AttributeKey<Long> SCHEMA_ID = AttributeKey.longKey("iceberg.schema.id");
  private static final AttributeKey<String> TABLE_NAME =
      AttributeKey.stringKey("iceberg.table.name");
  private static final AttributeKey<Long> SNAPHSOT_ID = AttributeKey.longKey("iceberg.snapshot.id");
  private static final AttributeKey<Long> SEQUENCE_NUMBER =
      AttributeKey.longKey("iceberg.commit.sequence_number");

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
      LongGauge metric =
          ScanMetricsBuilder.totalPlanningDuration(
              openTelemetry.getMeter(INSTRUMENTATION_NAME),
              duration.timeUnit().name().toLowerCase(Locale.getDefault()));
      metric.set(duration.totalDuration().toMillis(), scanAttributes);
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

  void reportCommitMetrics(CommitReport commitReport) {
    Attributes commitAttributes =
        Attributes.of(
            SEQUENCE_NUMBER,
            Long.valueOf(commitReport.sequenceNumber()),
            TABLE_NAME,
            commitReport.tableName(),
            SNAPHSOT_ID,
            commitReport.snapshotId());
    CommitMetricsResult metrics = commitReport.commitMetrics();
    TimerResult duration = metrics.totalDuration();

    if (duration != null) {
      LongGauge metric =
          CommitMetricsBuilder.duration(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.set(duration.totalDuration().toMillis(), commitAttributes);
    }

    CounterResult current = metrics.attempts();

    if (current != null) {
      LongCounter metric =
          CommitMetricsBuilder.attempts(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), commitAttributes);
    }

    current = metrics.addedDataFiles();

    if (current != null) {
      LongCounter metric =
          CommitMetricsBuilder.addedDataFiles(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), commitAttributes);
    }

    current = metrics.removedDataFiles();

    if (current != null) {
      LongCounter metric =
          CommitMetricsBuilder.removedDataFiles(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), commitAttributes);
    }

    current = metrics.totalDataFiles();

    if (current != null) {
      LongCounter metric =
          CommitMetricsBuilder.totalDataFiles(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), commitAttributes);
    }

    current = metrics.addedDeleteFiles();

    if (current != null) {
      LongCounter metric =
          CommitMetricsBuilder.addedDeleteFiles(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), commitAttributes);
    }

    current = metrics.addedEqualityDeleteFiles();

    if (current != null) {
      LongCounter metric =
          CommitMetricsBuilder.addedEqualityDeleteFiles(
              openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), commitAttributes);
    }

    current = metrics.addedPositionalDeleteFiles();

    if (current != null) {
      LongCounter metric =
          CommitMetricsBuilder.addedPositionDeleteFiles(
              openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), commitAttributes);
    }

    current = metrics.addedDVs();

    if (current != null) {
      LongCounter metric =
          CommitMetricsBuilder.addedDeletionVectors(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), commitAttributes);
    }

    current = metrics.removedPositionalDeleteFiles();

    if (current != null) {
      LongCounter metric =
          CommitMetricsBuilder.removedPositionDeleteFiles(
              openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), commitAttributes);
    }

    current = metrics.removedDVs();

    if (current != null) {
      LongCounter metric =
          CommitMetricsBuilder.removedDeletionVectors(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), commitAttributes);
    }

    current = metrics.removedEqualityDeleteFiles();

    if (current != null) {
      LongCounter metric =
          CommitMetricsBuilder.removedEqualityDeleteFiles(
              openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), commitAttributes);
    }

    current = metrics.removedDeleteFiles();

    if (current != null) {
      LongCounter metric =
          CommitMetricsBuilder.removedDeleteFiles(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), commitAttributes);
    }

    current = metrics.totalDataFiles();

    if (current != null) {
      LongCounter metric =
          CommitMetricsBuilder.totalDataFiles(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), commitAttributes);
    }

    current = metrics.addedRecords();

    if (current != null) {
      LongCounter metric =
          CommitMetricsBuilder.addedRecords(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), commitAttributes);
    }

    current = metrics.removedRecords();

    if (current != null) {
      LongCounter metric =
          CommitMetricsBuilder.removedRecords(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), commitAttributes);
    }

    current = metrics.totalRecords();

    if (current != null) {
      LongCounter metric =
          CommitMetricsBuilder.totalRecords(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), commitAttributes);
    }

    current = metrics.addedFilesSizeInBytes();

    if (current != null) {
      LongCounter metric =
          CommitMetricsBuilder.addedFilesSize(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), commitAttributes);
    }

    current = metrics.removedFilesSizeInBytes();

    if (current != null) {
      LongCounter metric =
          CommitMetricsBuilder.removedFilesSize(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), commitAttributes);
    }

    current = metrics.totalFilesSizeInBytes();

    if (current != null) {
      LongCounter metric =
          CommitMetricsBuilder.totalFilesSize(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), commitAttributes);
    }

    current = metrics.addedPositionalDeletes();

    if (current != null) {
      LongCounter metric =
          CommitMetricsBuilder.addedPositionDeletes(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), commitAttributes);
    }

    current = metrics.removedPositionalDeletes();

    if (current != null) {
      LongCounter metric =
          CommitMetricsBuilder.removedPositionDeletes(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), commitAttributes);
    }

    current = metrics.totalPositionalDeletes();

    if (current != null) {
      LongCounter metric =
          CommitMetricsBuilder.totalPositionDeletes(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), commitAttributes);
    }

    current = metrics.addedEqualityDeletes();

    if (current != null) {
      LongCounter metric =
          CommitMetricsBuilder.addedEqualityDeletes(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), commitAttributes);
    }

    current = metrics.removedEqualityDeletes();

    if (current != null) {
      LongCounter metric =
          CommitMetricsBuilder.removedEqualityDeletes(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), commitAttributes);
    }

    current = metrics.totalEqualityDeletes();

    if (current != null) {
      LongCounter metric =
          CommitMetricsBuilder.totalEqualityDeletes(openTelemetry.getMeter(INSTRUMENTATION_NAME));
      metric.add(current.value(), commitAttributes);
    }
  }
}
