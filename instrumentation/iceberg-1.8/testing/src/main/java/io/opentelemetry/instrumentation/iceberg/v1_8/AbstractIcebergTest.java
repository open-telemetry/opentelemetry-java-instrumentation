/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.iceberg.v1_8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.TableScan;
import org.apache.iceberg.TestTables;
import org.apache.iceberg.TestTables.TestTable;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.metrics.CounterResult;
import org.apache.iceberg.metrics.MetricsReport;
import org.apache.iceberg.metrics.MetricsReporter;
import org.apache.iceberg.metrics.ScanReport;
import org.apache.iceberg.metrics.TimerResult;
import org.apache.iceberg.types.Types.IntegerType;
import org.apache.iceberg.types.Types.NestedField;
import org.apache.iceberg.types.Types.StringType;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public abstract class AbstractIcebergTest {
  protected static final int FORMAT_VERSION = 2;
  protected static final Schema SCHEMA =
      new Schema(
          NestedField.required(3, "id", IntegerType.get()),
          NestedField.required(4, "data", StringType.get()));
  protected static final int BUCKETS_NUMBER = 16;
  protected static final PartitionSpec SPEC =
      PartitionSpec.builderFor(SCHEMA).bucket("data", 16).build();
  protected static final DataFile FILE_1 =
      DataFiles.builder(SPEC)
          .withPath("/path/to/data-a.parquet")
          .withFileSizeInBytes(10L)
          .withPartitionPath("data_bucket=0")
          .withRecordCount(1L)
          .build();
  protected static final DataFile FILE_2 =
      DataFiles.builder(SPEC)
          .withPath("/path/to/data-b.parquet")
          .withFileSizeInBytes(10L)
          .withPartitionPath("data_bucket=1")
          .withRecordCount(1L)
          .withSplitOffsets(Arrays.asList(1L))
          .build();

  @TempDir protected File tableDir = null;
  protected TestTable table;

  protected abstract InstrumentationExtension testing();

  protected abstract TableScan configure(TableScan tableScan);

  @BeforeEach
  void init() {
    this.table = TestTables.create(this.tableDir, "test", SCHEMA, SPEC, FORMAT_VERSION);
    this.table.newFastAppend().appendFile(FILE_1).appendFile(FILE_2).commit();
  }

  @Test
  void testCreateTelemetry() throws IOException {

    SimpleReporter reporter = new SimpleReporter();
    TableScan scan =
        table
            .newScan()
            .filter(Expressions.lessThan("id", 5))
            .select("id", "data")
            .metricsReporter(reporter);
    scan = configure(scan);

    try (CloseableIterable<FileScanTask> tasks = scan.planFiles()) {
      assertNotNull(tasks);
      assertNotNull(tasks.iterator());
    }

    assertNotNull(reporter.report);
    assertTrue(reporter.report instanceof ScanReport);
    ScanReport expected = (ScanReport) reporter.report;
    CounterResult currentExpectedMetric = expected.scanMetrics().resultDataFiles();

    if (currentExpectedMetric != null) {
      assertIcebergCounterMetric(
          "iceberg.scan.scanned.data_files.count",
          "{file}",
          expected,
          currentExpectedMetric.value());
    } else {
      assertIcebergMetricNotReported("iceberg.scan.scanned.data_files.count");
    }

    currentExpectedMetric = expected.scanMetrics().resultDeleteFiles();

    if (currentExpectedMetric != null) {
      assertIcebergCounterMetric(
          "iceberg.scan.scanned.delete_files.count",
          "{file}",
          expected,
          currentExpectedMetric.value());
    } else {
      assertIcebergMetricNotReported("iceberg.scan.scanned.delete_files.count");
    }

    currentExpectedMetric = expected.scanMetrics().scannedDataManifests();

    if (currentExpectedMetric != null) {
      assertIcebergCounterMetric(
          "iceberg.scan.scanned.data_manifests.count",
          "{file}",
          expected,
          currentExpectedMetric.value());
    } else {
      assertIcebergMetricNotReported("iceberg.scan.scanned.data_manifests.count");
    }

    currentExpectedMetric = expected.scanMetrics().scannedDeleteManifests();

    if (currentExpectedMetric != null) {
      assertIcebergCounterMetric(
          "iceberg.scan.scanned.delete_manifests.count",
          "{file}",
          expected,
          currentExpectedMetric.value());
    } else {
      assertIcebergMetricNotReported("iceberg.scan.scanned.delete_manifests.count");
    }

    currentExpectedMetric = expected.scanMetrics().totalDataManifests();

    if (currentExpectedMetric != null) {
      assertIcebergCounterMetric(
          "iceberg.scan.total.data_manifests.count",
          "{file}",
          expected,
          currentExpectedMetric.value());
    } else {
      assertIcebergMetricNotReported("iceberg.scan.total.data_manifests.count");
    }

    currentExpectedMetric = expected.scanMetrics().totalDeleteManifests();

    if (currentExpectedMetric != null) {
      assertIcebergCounterMetric(
          "iceberg.scan.total.delete_manifests.count",
          "{file}",
          expected,
          currentExpectedMetric.value());
    } else {
      assertIcebergMetricNotReported("iceberg.scan.total.delete_manifests.count");
    }

    currentExpectedMetric = expected.scanMetrics().totalFileSizeInBytes();

    if (currentExpectedMetric != null) {
      assertIcebergCounterMetric(
          "iceberg.scan.scanned.data_files.size", "By", expected, currentExpectedMetric.value());
    } else {
      assertIcebergMetricNotReported("iceberg.scan.scanned.data_files.size");
    }

    currentExpectedMetric = expected.scanMetrics().totalDeleteFileSizeInBytes();

    if (currentExpectedMetric != null) {
      assertIcebergCounterMetric(
          "iceberg.scan.scanned.delete_files.size", "By", expected, currentExpectedMetric.value());
    } else {
      assertIcebergMetricNotReported("iceberg.scan.scanned.delete_files.size");
    }

    currentExpectedMetric = expected.scanMetrics().skippedDataManifests();

    if (currentExpectedMetric != null) {
      assertIcebergCounterMetric(
          "iceberg.scan.skipped.data_manifests.count",
          "{file}",
          expected,
          currentExpectedMetric.value());
    } else {
      assertIcebergMetricNotReported("iceberg.scan.skipped.data_manifests.count");
    }

    currentExpectedMetric = expected.scanMetrics().skippedDeleteManifests();

    if (currentExpectedMetric != null) {
      assertIcebergCounterMetric(
          "iceberg.scan.skipped.delete_manifests.count",
          "{file}",
          expected,
          currentExpectedMetric.value());
    } else {
      assertIcebergMetricNotReported("iceberg.scan.skipped.delete_manifests.count");
    }

    currentExpectedMetric = expected.scanMetrics().skippedDataFiles();

    if (currentExpectedMetric != null) {
      assertIcebergCounterMetric(
          "iceberg.scan.skipped.data_files.count",
          "{file}",
          expected,
          currentExpectedMetric.value());
    } else {
      assertIcebergMetricNotReported("iceberg.scan.skipped.data_files.count");
    }

    currentExpectedMetric = expected.scanMetrics().skippedDeleteFiles();

    if (currentExpectedMetric != null) {
      assertIcebergCounterMetric(
          "iceberg.scan.skipped.delete_files.count",
          "{file}",
          expected,
          currentExpectedMetric.value());
    } else {
      assertIcebergMetricNotReported("iceberg.scan.skipped.delete_files.count");
    }

    currentExpectedMetric = expected.scanMetrics().indexedDeleteFiles();

    if (currentExpectedMetric != null) {
      assertIcebergCounterMetric(
          "iceberg.scan.scanned.indexed_delete_files.count",
          "{file}",
          expected,
          currentExpectedMetric.value());
    } else {
      assertIcebergMetricNotReported("iceberg.scan.scanned.indexed_delete_files.count");
    }

    currentExpectedMetric = expected.scanMetrics().equalityDeleteFiles();

    if (currentExpectedMetric != null) {
      assertIcebergCounterMetric(
          "iceberg.scan.scanned.equality_delete_files.count",
          "{file}",
          expected,
          currentExpectedMetric.value());
    } else {
      assertIcebergMetricNotReported("iceberg.scan.scanned.equality_delete_files.count");
    }

    currentExpectedMetric = expected.scanMetrics().positionalDeleteFiles();

    if (currentExpectedMetric != null) {
      assertIcebergCounterMetric(
          "iceberg.scan.scanned.positional_delete_files.count",
          "{file}",
          expected,
          currentExpectedMetric.value());
    } else {
      assertIcebergMetricNotReported("iceberg.scan.scanned.positional_delete_files.count");
    }

    currentExpectedMetric = expected.scanMetrics().dvs();

    if (currentExpectedMetric != null) {
      assertIcebergCounterMetric(
          "iceberg.scan.scanned.dvs.count", "{file}", expected, currentExpectedMetric.value());
    } else {
      assertIcebergMetricNotReported("iceberg.scan.scanned.dvs.count");
    }

    TimerResult timer = expected.scanMetrics().totalPlanningDuration();

    if (timer != null) {
      assertIcebergGaugeMetric(
          "iceberg.scan.planning.duration",
          timer.timeUnit().name().toLowerCase(Locale.getDefault()),
          expected,
          timer.totalDuration().toMillis());
    } else {
      assertIcebergMetricNotReported("iceberg.scan.planning.duration");
    }
  }

  private void assertIcebergMetricNotReported(String otelMetricName) {
    testing()
        .waitAndAssertMetrics(
            otelMetricName,
            metricAssert ->
                metricAssert.doesNotHave(
                    new Condition<>(
                        spanData -> otelMetricName.equals(spanData.getName()),
                        "metric is not reported")));
  }

  private void assertIcebergGaugeMetric(
      String otelMetricName, String expectedUnit, ScanReport expectedReport, long expectedValue) {
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.iceberg-1.8",
            metricAssert ->
                metricAssert
                    .hasName(otelMetricName)
                    .hasUnit(expectedUnit)
                    .hasLongGaugeSatisfying(
                        sum ->
                            sum.hasPointsSatisfying(
                                longAssert ->
                                    longAssert
                                        .hasValue(expectedValue)
                                        .hasAttributesSatisfying(
                                            attributes ->
                                                assertEquals(
                                                    Attributes.builder()
                                                        .put(
                                                            "iceberg.schema.id",
                                                            expectedReport.schemaId())
                                                        .put(
                                                            "iceberg.table.name",
                                                            expectedReport.tableName())
                                                        .put(
                                                            "iceberg.snapshot.id",
                                                            expectedReport.snapshotId())
                                                        .build(),
                                                    attributes)))));
  }

  private void assertIcebergCounterMetric(
      String otelMetricName, String expectedUnit, ScanReport expectedReport, long expectedValue) {
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.iceberg-1.8",
            metricAssert ->
                metricAssert
                    .hasName(otelMetricName)
                    .hasUnit(expectedUnit)
                    .hasLongSumSatisfying(
                        sum ->
                            sum.hasPointsSatisfying(
                                longSumAssert ->
                                    longSumAssert
                                        .hasValue(expectedValue)
                                        .hasAttributesSatisfying(
                                            attributes ->
                                                assertEquals(
                                                    Attributes.builder()
                                                        .put(
                                                            "iceberg.schema.id",
                                                            expectedReport.schemaId())
                                                        .put(
                                                            "iceberg.table.name",
                                                            expectedReport.tableName())
                                                        .put(
                                                            "iceberg.snapshot.id",
                                                            expectedReport.snapshotId())
                                                        .build(),
                                                    attributes)))));
  }

  static final class SimpleReporter implements MetricsReporter {
    MetricsReport report;

    @Override
    public void report(MetricsReport report) {
      this.report = report;
    }
  }
}
