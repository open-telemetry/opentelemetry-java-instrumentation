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
import io.opentelemetry.api.metrics.Meter;
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
  private static final AttributeKey<Long> SNAPSHOT_ID = AttributeKey.longKey("iceberg.snapshot.id");
  private static final AttributeKey<String> SCAN_STATE =
      AttributeKey.stringKey("iceberg.scan.state");
  private static final AttributeKey<String> DELETE_TYPE =
      AttributeKey.stringKey("iceberg.delete_file.type");
  private final DoubleHistogram planningDuration;
  private final LongCounter dataFilesCount;
  private final LongCounter dataFilesSize;
  private final LongCounter deleteFilesCount;
  private final LongCounter deleteFilesSize;
  private final LongCounter dataManifestsCount;
  private final LongCounter deleteManifestsCount;

  IcebergMetricsReporter(OpenTelemetry openTelemetry) {
    Meter meter = openTelemetry.getMeter(INSTRUMENTATION_NAME);
    planningDuration = ScanMetricsBuilder.totalPlanningDuration(meter, "s");
    dataFilesCount = ScanMetricsBuilder.dataFilesCount(meter);
    dataFilesSize = ScanMetricsBuilder.dataFilesSize(meter);
    deleteFilesCount = ScanMetricsBuilder.deleteFilesCount(meter);
    deleteFilesSize = ScanMetricsBuilder.deleteFilesSize(meter);
    dataManifestsCount = ScanMetricsBuilder.dataManifestsCount(meter);
    deleteManifestsCount = ScanMetricsBuilder.deleteManifestsCount(meter);
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
            SNAPSHOT_ID,
            scanReport.snapshotId());
    ScanMetricsResult metrics = scanReport.scanMetrics();
    TimerResult duration = metrics.totalPlanningDuration();

    if (duration != null) {
      planningDuration.record(duration.totalDuration().toMillis() / 1000.0, scanAttributes);
    }

    // Data files metrics
    CounterResult current = metrics.resultDataFiles();

    if (current != null) {
      addValueToLongCounter(dataFilesCount, current.value(), scanAttributes, SCAN_STATE, "scanned");
    }

    current = metrics.skippedDataFiles();

    if (current != null) {
      addValueToLongCounter(dataFilesCount, current.value(), scanAttributes, SCAN_STATE, "skipped");
    }

    current = metrics.totalFileSizeInBytes();

    if (current != null) {
      dataFilesSize.add(current.value(), scanAttributes);
    }

    // Delete files metrics
    current = metrics.totalDeleteFileSizeInBytes();

    if (current != null) {
      deleteFilesSize.add(current.value(), scanAttributes);
    }

    current = metrics.resultDeleteFiles();

    if (current != null) {
      addValueToLongCounter(
          deleteFilesCount,
          current.value(),
          scanAttributes,
          SCAN_STATE,
          "scanned",
          DELETE_TYPE,
          "all");
    }

    current = metrics.skippedDeleteFiles();

    if (current != null) {
      addValueToLongCounter(
          deleteFilesCount,
          current.value(),
          scanAttributes,
          SCAN_STATE,
          "skipped",
          DELETE_TYPE,
          "all");
    }

    current = metrics.indexedDeleteFiles();

    if (current != null) {
      addValueToLongCounter(
          deleteFilesCount,
          current.value(),
          scanAttributes,
          SCAN_STATE,
          "scanned",
          DELETE_TYPE,
          "indexed");
    }

    current = metrics.equalityDeleteFiles();

    if (current != null) {
      addValueToLongCounter(
          deleteFilesCount,
          current.value(),
          scanAttributes,
          SCAN_STATE,
          "scanned",
          DELETE_TYPE,
          "equality");
    }

    current = metrics.positionalDeleteFiles();

    if (current != null) {
      addValueToLongCounter(
          deleteFilesCount,
          current.value(),
          scanAttributes,
          SCAN_STATE,
          "scanned",
          DELETE_TYPE,
          "position");
    }

    current = metrics.dvs();

    if (current != null) {
      addValueToLongCounter(
          deleteFilesCount,
          current.value(),
          scanAttributes,
          SCAN_STATE,
          "scanned",
          DELETE_TYPE,
          "dvs");
    }

    // Data manifests metrics
    current = metrics.scannedDataManifests();

    if (current != null) {
      addValueToLongCounter(
          dataManifestsCount, current.value(), scanAttributes, SCAN_STATE, "scanned");
    }

    current = metrics.skippedDataManifests();

    if (current != null) {
      addValueToLongCounter(
          dataManifestsCount, current.value(), scanAttributes, SCAN_STATE, "skipped");
    }

    // Delete manifests metrics
    current = metrics.scannedDeleteManifests();

    if (current != null) {
      addValueToLongCounter(
          deleteManifestsCount, current.value(), scanAttributes, SCAN_STATE, "scanned");
    }

    current = metrics.skippedDeleteManifests();

    if (current != null) {
      addValueToLongCounter(
          deleteManifestsCount, current.value(), scanAttributes, SCAN_STATE, "skipped");
    }
  }

  private static void addValueToLongCounter(
      LongCounter metric,
      long measurement,
      Attributes attributes,
      AttributeKey<String> att1Key,
      String att1Value) {
    Attributes newAttributes = attributes.toBuilder().put(att1Key, att1Value).build();
    metric.add(measurement, newAttributes);
  }

  private static void addValueToLongCounter(
      LongCounter metric,
      long measurement,
      Attributes attributes,
      AttributeKey<String> att1Key,
      String att1Value,
      AttributeKey<String> att2Key,
      String att2Value) {
    Attributes newAttributes =
        attributes.toBuilder().put(att1Key, att1Value).put(att2Key, att2Value).build();
    metric.add(measurement, newAttributes);
  }
}
