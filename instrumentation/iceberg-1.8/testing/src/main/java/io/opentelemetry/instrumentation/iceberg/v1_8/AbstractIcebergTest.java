package io.opentelemetry.instrumentation.iceberg.v1_8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.TableScan;
import org.apache.iceberg.TestTables;
import org.apache.iceberg.TestTables.TestTable;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.metrics.MetricsReport;
import org.apache.iceberg.metrics.MetricsReporter;
import org.apache.iceberg.types.Types.IntegerType;
import org.apache.iceberg.types.Types.NestedField;
import org.apache.iceberg.types.Types.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;

abstract class AbstractIcebergTest {
  protected abstract InstrumentationExtension testing();

  protected abstract TableScan configure(TableScan tableScan);

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractIcebergTest.class);
  protected static final int FORMAT_VERSION = 2;
  protected static final Schema SCHEMA = new Schema(NestedField.required(3, "id", IntegerType.get()), NestedField.required(4, "data", StringType.get()));
  protected static final int BUCKETS_NUMBER = 16;
  protected static final PartitionSpec SPEC = PartitionSpec.builderFor(SCHEMA).bucket("data", 16).build();
  protected static final DataFile FILE_1 = DataFiles.builder(SPEC).withPath("/path/to/data-a.parquet").withFileSizeInBytes(10L).withPartitionPath("data_bucket=0").withRecordCount(1L).build();
  protected static final DataFile FILE_2 = DataFiles.builder(SPEC).withPath("/path/to/data-b.parquet").withFileSizeInBytes(10L).withPartitionPath("data_bucket=1").withRecordCount(1L).withSplitOffsets(Arrays.asList(1L)).build();

  @TempDir
  protected File tableDir = null;
  protected TestTable table;

  @BeforeEach
  void init() {
    this.table = TestTables.create(this.tableDir, "test", SCHEMA, SPEC, FORMAT_VERSION);
    this.table.newFastAppend().appendFile(FILE_1).appendFile(FILE_2).commit();
  }

  @Test
  void testCreateTelemetry() throws IOException {
    SimpleReporter reporter = new SimpleReporter();

    TableScan scan = table.newScan()
      .select("id", "data");
    assertNotNull(scan);
    assertNull(reporter.report);
      
    Schema projection = scan.schema();
    assertNotNull(projection);

    try (CloseableIterable<FileScanTask> tasks = scan.planFiles()) {
      assertNotNull(tasks);
      int counter = 0;

      for (FileScanTask fileTask : tasks) {
        LOGGER.info(fileTask.file().location());
        counter++;
      }

      assertEquals(2, counter);
      assertNotNull(reporter.report);
    }
  }

  static class SimpleReporter implements MetricsReporter {
    MetricsReport report;

    @Override
    public void report(MetricsReport report) {
      LOGGER.error("I am invoked!");
      this.report = report;
    }

  }

}
