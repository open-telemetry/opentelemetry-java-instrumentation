/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.iceberg.v1_8;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableScan;
import org.apache.iceberg.TestTables;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.metrics.MetricsReport;
import org.apache.iceberg.metrics.MetricsReporter;
import org.apache.iceberg.metrics.ScanReport;
import org.apache.iceberg.types.Types.IntegerType;
import org.apache.iceberg.types.Types.NestedField;
import org.apache.iceberg.types.Types.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public abstract class AbstractIcebergTest {
  protected static final int FORMAT_VERSION = 2;
  protected static final Schema SCHEMA =
      new Schema(
          NestedField.required(3, "id", IntegerType.get()),
          NestedField.required(4, "data", StringType.get()));
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
  protected Table table;

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
      assertThat(tasks).isNotNull();
      assertThat(tasks.iterator()).isNotNull();
    }

    assertThat(reporter.report).isNotNull();
    assertThat(reporter.report).isInstanceOf(ScanReport.class);
    ScanReport expected = (ScanReport) reporter.report;

    assertScanDurationMetric(expected);
    assertDataFilesCountMetrics(expected);
    assertDeleteFilesCountMetrics(expected);
    assertDataManifestCountMetrics(expected);
    assertDeleteManifestCountMetrics(expected);
    assertSizeMetric(
        "iceberg.scan.data_files.size",
        expected,
        expected.scanMetrics().totalFileSizeInBytes().value());
    assertSizeMetric(
        "iceberg.scan.delete_files.size",
        expected,
        expected.scanMetrics().totalDeleteFileSizeInBytes().value());
  }

  private void assertScanDurationMetric(ScanReport expectedReport) {
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.iceberg-1.8",
            metricAssert ->
                metricAssert
                    .hasName("iceberg.scan.planning.duration")
                    .hasUnit("s")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSumGreaterThan(0.0)
                                        .hasCount(1)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(
                                                longKey("iceberg.schema.id"),
                                                expectedReport.schemaId()),
                                            equalTo(
                                                stringKey("iceberg.table.name"),
                                                expectedReport.tableName())))));
  }

  private void assertDataFilesCountMetrics(ScanReport expectedReport) {
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.iceberg-1.8",
            metricAssert ->
                metricAssert
                    .hasName("iceberg.scan.data_files.count")
                    .hasUnit("{file}")
                    .hasLongSumSatisfying(
                        sum ->
                            sum.hasPointsSatisfying(
                                longSumAssert ->
                                    longSumAssert
                                        .hasValue(
                                            expectedReport.scanMetrics().resultDataFiles().value())
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("iceberg.scan.state"), "scanned"),
                                            equalTo(
                                                longKey("iceberg.schema.id"),
                                                expectedReport.schemaId()),
                                            equalTo(
                                                stringKey("iceberg.table.name"),
                                                expectedReport.tableName())),
                                longSumAssert ->
                                    longSumAssert
                                        .hasValue(
                                            expectedReport.scanMetrics().skippedDataFiles().value())
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("iceberg.scan.state"), "skipped"),
                                            equalTo(
                                                longKey("iceberg.schema.id"),
                                                expectedReport.schemaId()),
                                            equalTo(
                                                stringKey("iceberg.table.name"),
                                                expectedReport.tableName())))));
  }

  private void assertDataManifestCountMetrics(ScanReport expectedReport) {
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.iceberg-1.8",
            metricAssert ->
                metricAssert
                    .hasName("iceberg.scan.data_manifests.count")
                    .hasUnit("{file}")
                    .hasLongSumSatisfying(
                        sum ->
                            sum.hasPointsSatisfying(
                                longSumAssert ->
                                    longSumAssert
                                        .hasValue(
                                            expectedReport
                                                .scanMetrics()
                                                .scannedDataManifests()
                                                .value())
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("iceberg.scan.state"), "scanned"),
                                            equalTo(
                                                longKey("iceberg.schema.id"),
                                                expectedReport.schemaId()),
                                            equalTo(
                                                stringKey("iceberg.table.name"),
                                                expectedReport.tableName())),
                                longSumAssert ->
                                    longSumAssert
                                        .hasValue(
                                            expectedReport
                                                .scanMetrics()
                                                .skippedDataManifests()
                                                .value())
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("iceberg.scan.state"), "skipped"),
                                            equalTo(
                                                longKey("iceberg.schema.id"),
                                                expectedReport.schemaId()),
                                            equalTo(
                                                stringKey("iceberg.table.name"),
                                                expectedReport.tableName())))));
  }

  private void assertDeleteManifestCountMetrics(ScanReport expectedReport) {
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.iceberg-1.8",
            metricAssert ->
                metricAssert
                    .hasName("iceberg.scan.delete_manifests.count")
                    .hasUnit("{file}")
                    .hasLongSumSatisfying(
                        sum ->
                            sum.hasPointsSatisfying(
                                longSumAssert ->
                                    longSumAssert
                                        .hasValue(
                                            expectedReport
                                                .scanMetrics()
                                                .scannedDeleteManifests()
                                                .value())
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("iceberg.scan.state"), "scanned"),
                                            equalTo(
                                                longKey("iceberg.schema.id"),
                                                expectedReport.schemaId()),
                                            equalTo(
                                                stringKey("iceberg.table.name"),
                                                expectedReport.tableName())),
                                longSumAssert ->
                                    longSumAssert
                                        .hasValue(
                                            expectedReport
                                                .scanMetrics()
                                                .skippedDeleteManifests()
                                                .value())
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("iceberg.scan.state"), "skipped"),
                                            equalTo(
                                                longKey("iceberg.schema.id"),
                                                expectedReport.schemaId()),
                                            equalTo(
                                                stringKey("iceberg.table.name"),
                                                expectedReport.tableName())))));
  }

  private void assertDeleteFilesCountMetrics(ScanReport expectedReport) {
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.iceberg-1.8",
            metricAssert ->
                metricAssert
                    .hasName("iceberg.scan.delete_files.count")
                    .hasUnit("{file}")
                    .hasLongSumSatisfying(
                        sum ->
                            sum.hasPointsSatisfying(
                                longSumAssert ->
                                    longSumAssert
                                        .hasValue(
                                            expectedReport
                                                .scanMetrics()
                                                .resultDeleteFiles()
                                                .value())
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("iceberg.scan.state"), "scanned"),
                                            equalTo(stringKey("iceberg.delete_file.type"), "all"),
                                            equalTo(
                                                longKey("iceberg.schema.id"),
                                                expectedReport.schemaId()),
                                            equalTo(
                                                stringKey("iceberg.table.name"),
                                                expectedReport.tableName())),
                                longSumAssert ->
                                    longSumAssert
                                        .hasValue(
                                            expectedReport
                                                .scanMetrics()
                                                .skippedDeleteFiles()
                                                .value())
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("iceberg.scan.state"), "skipped"),
                                            equalTo(stringKey("iceberg.delete_file.type"), "all"),
                                            equalTo(
                                                longKey("iceberg.schema.id"),
                                                expectedReport.schemaId()),
                                            equalTo(
                                                stringKey("iceberg.table.name"),
                                                expectedReport.tableName())),
                                longSumAssert ->
                                    longSumAssert
                                        .hasValue(
                                            expectedReport
                                                .scanMetrics()
                                                .indexedDeleteFiles()
                                                .value())
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("iceberg.scan.state"), "scanned"),
                                            equalTo(
                                                stringKey("iceberg.delete_file.type"), "indexed"),
                                            equalTo(
                                                longKey("iceberg.schema.id"),
                                                expectedReport.schemaId()),
                                            equalTo(
                                                stringKey("iceberg.table.name"),
                                                expectedReport.tableName())),
                                longSumAssert ->
                                    longSumAssert
                                        .hasValue(
                                            expectedReport
                                                .scanMetrics()
                                                .equalityDeleteFiles()
                                                .value())
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("iceberg.scan.state"), "scanned"),
                                            equalTo(
                                                stringKey("iceberg.delete_file.type"), "equality"),
                                            equalTo(
                                                longKey("iceberg.schema.id"),
                                                expectedReport.schemaId()),
                                            equalTo(
                                                stringKey("iceberg.table.name"),
                                                expectedReport.tableName())),
                                longSumAssert ->
                                    longSumAssert
                                        .hasValue(
                                            expectedReport
                                                .scanMetrics()
                                                .positionalDeleteFiles()
                                                .value())
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("iceberg.scan.state"), "scanned"),
                                            equalTo(
                                                stringKey("iceberg.delete_file.type"), "position"),
                                            equalTo(
                                                longKey("iceberg.schema.id"),
                                                expectedReport.schemaId()),
                                            equalTo(
                                                stringKey("iceberg.table.name"),
                                                expectedReport.tableName())),
                                longSumAssert ->
                                    longSumAssert
                                        .hasValue(expectedReport.scanMetrics().dvs().value())
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("iceberg.scan.state"), "scanned"),
                                            equalTo(stringKey("iceberg.delete_file.type"), "dvs"),
                                            equalTo(
                                                longKey("iceberg.schema.id"),
                                                expectedReport.schemaId()),
                                            equalTo(
                                                stringKey("iceberg.table.name"),
                                                expectedReport.tableName())))));
  }

  private void assertSizeMetric(
      String otelMetricName, ScanReport expectedReport, long expectedValue) {
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.iceberg-1.8",
            metricAssert ->
                metricAssert
                    .hasName(otelMetricName)
                    .hasUnit("By")
                    .hasLongSumSatisfying(
                        sum ->
                            sum.hasPointsSatisfying(
                                longSumAssert ->
                                    longSumAssert
                                        .hasValue(expectedValue)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(
                                                longKey("iceberg.schema.id"),
                                                expectedReport.schemaId()),
                                            equalTo(
                                                stringKey("iceberg.table.name"),
                                                expectedReport.tableName())))));
  }

  static final class SimpleReporter implements MetricsReporter {
    MetricsReport report;

    @Override
    public void report(MetricsReport report) {
      this.report = report;
    }
  }
}
