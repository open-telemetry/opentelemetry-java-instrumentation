/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.testing;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStableDbSystemName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.CompareOperator;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.NamespaceDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Append;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptor;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Increment;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Row;
import org.apache.hadoop.hbase.client.RowMutations;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.ipc.CallTimeoutException;
import org.apache.hadoop.hbase.util.Bytes;
import org.assertj.core.api.AbstractAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.utility.DockerImageName;

@SuppressWarnings("deprecation") // using deprecated semconv
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractHbaseTest {

  protected static final int MASTER_PORT = 16000;
  protected static final int REGION_SERVER_PORT = 16020;
  protected static final String INSTRUMENTATION_NAME = "io.opentelemetry.hbase-client-2.0";

  private static final String NAMESPACE = "ot_test";
  protected static final byte[] COLUMN_FAMILY = Bytes.toBytes("cf");
  protected static final TableName TABLE_NAME = TableName.valueOf("ot_test:eleven_test_table");
  protected static final TableName META = TableName.valueOf("hbase:meta");

  private static final String DB_SYSTEM_VALUE = "hbase";
  protected static final String SCAN = "Scan";
  protected static final String MUTATE = "Mutate";
  protected static final String GET = "Get";
  protected static final String MULTI = "Multi";

  private static final int GET_TIMEOUT_OPERATION_TIMEOUT_MILLIS = 1000;
  private static final int GET_TIMEOUT_RPC_TIMEOUT_MILLIS = 200;
  private static final String ROW_1 = "row1";
  private static final String ROW_2 = "row2";
  private static final String ROW_3 = "row3";
  private static final String ROW_4 = "row4";
  private static final String SCAN_ROW = "scan-row";

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private final String hostname = getHostName();
  protected final GenericContainer<?> hbaseContainer = createHbaseContainer(hostname);

  protected Connection connection;

  protected abstract InstrumentationExtension testing();

  private static String getHostName() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      return "localhost";
    }
  }

  private static GenericContainer<?> createHbaseContainer(String hostname) {
    return new GenericContainer<>(DockerImageName.parse("harisekhon/hbase:2.0"))
        .withCreateContainerCmdModifier(
            cmd ->
                cmd.getHostConfig()
                    .withPortBindings(
                        new PortBinding(Ports.Binding.bindPort(2181), new ExposedPort(2181)),
                        new PortBinding(Ports.Binding.bindPort(16000), new ExposedPort(16000)),
                        new PortBinding(Ports.Binding.bindPort(16010), new ExposedPort(16010)),
                        new PortBinding(Ports.Binding.bindPort(16020), new ExposedPort(16020)),
                        new PortBinding(Ports.Binding.bindPort(16030), new ExposedPort(16030)),
                        new PortBinding(Ports.Binding.bindPort(16201), new ExposedPort(16201)),
                        new PortBinding(Ports.Binding.bindPort(16301), new ExposedPort(16301))))
        .withExposedPorts(2181, 16000, 16010, 16020, 16030, 16201, 16301)
        .withStartupTimeout(Duration.ofMinutes(2))
        .withCreateContainerCmdModifier(cmd -> cmd.withHostName(hostname))
        .waitingFor(
            new WaitAllStrategy()
                .withStrategy(Wait.forLogMessage(".*Master has completed initialization.*\\n", 1))
                .withStrategy(Wait.forListeningPorts(2181, MASTER_PORT, REGION_SERVER_PORT))
                .withStartupTimeout(Duration.ofMinutes(2)));
  }

  @BeforeAll
  void setUp() throws IOException {
    hbaseContainer.start();
    cleanup.deferAfterAll(hbaseContainer::stop);
    String host = hbaseContainer.getHost();
    Configuration config = HBaseConfiguration.create();
    config.set("hbase.zookeeper.quorum", host);
    config.set("hbase.zookeeper.property.clientPort", "2181");
    connection = ConnectionFactory.createConnection(config);
    cleanup.deferAfterAll(connection);
    testing()
        .runWithSpan(
            "setup",
            () -> {
              createNamespaceAndTable();
              seedRows();
            });
    testing().waitForTraces(1);
    testing().clearData();
  }

  private void createNamespaceAndTable() throws IOException {
    try (Admin admin = connection.getAdmin()) {
      NamespaceDescriptor namespaceDescriptor = NamespaceDescriptor.create(NAMESPACE).build();
      admin.createNamespace(namespaceDescriptor);
      ColumnFamilyDescriptor columnFamilyDescriptor =
          ColumnFamilyDescriptorBuilder.newBuilder(COLUMN_FAMILY).build();
      TableDescriptor tableDescriptor =
          TableDescriptorBuilder.newBuilder(TABLE_NAME)
              .setColumnFamily(columnFamilyDescriptor)
              .build();
      admin.createTable(tableDescriptor);
    }
  }

  private void seedRows() throws IOException {
    try (Table table = connection.getTable(TABLE_NAME)) {
      table.put(row(ROW_1, "col1_val_1", "col2_val_1"));
      table.put(row(SCAN_ROW, "scan_col1_val", "scan_col2_val"));
    }
  }

  private static Put row(String rowKey, String col1Value, String col2Value) {
    Put put = new Put(Bytes.toBytes(rowKey));
    put.addColumn(COLUMN_FAMILY, Bytes.toBytes("col1"), Bytes.toBytes(col1Value));
    put.addColumn(COLUMN_FAMILY, Bytes.toBytes("col2"), Bytes.toBytes(col2Value));
    return put;
  }

  @Test
  void testListNamespace() throws IOException {
    Admin admin = connection.getAdmin();
    cleanup.deferCleanup(admin);

    List<String> namespaces = new ArrayList<>();
    for (NamespaceDescriptor ns : admin.listNamespaceDescriptors()) {
      namespaces.add(ns.getName());
    }
    assertThat(namespaces).contains(NAMESPACE);

    testing()
        .waitAndAssertTraces(
            traceAssertConsumer(null, "IsMasterRunning", MASTER_PORT, false),
            traceAssertConsumer(null, "ListNamespaceDescriptors", MASTER_PORT, false));
  }

  @Test
  void testListTable() throws IOException {
    Admin admin = connection.getAdmin();
    cleanup.deferCleanup(admin);

    assertThat(admin.listTableNames()).contains(TABLE_NAME);

    testing()
        .waitAndAssertTraces(
            traceAssertConsumer(null, "IsMasterRunning", MASTER_PORT, false),
            traceAssertConsumer(null, "GetTableNames", MASTER_PORT, false));
  }

  @Test
  void testPut() throws IOException {
    try (Connection putConnection =
            ConnectionFactory.createConnection(connection.getConfiguration());
        Table table = putConnection.getTable(TABLE_NAME)) {
      Put put = row("put-row", "put_col1_val", "put_col2_val");
      table.put(put);
    }
    testing()
        .waitAndAssertTraces(
            traceAssertConsumer(META, SCAN, REGION_SERVER_PORT, true),
            traceAssertConsumer(TABLE_NAME, MUTATE, REGION_SERVER_PORT, true));
  }

  @Test
  void testGet() throws IOException {
    try (Table table = connection.getTable(TABLE_NAME)) {
      Get get = new Get(Bytes.toBytes(ROW_1));
      Result result = table.get(get);
      assertThat(value(result, "col1")).isEqualTo("col1_val_1");
      assertThat(value(result, "col2")).isEqualTo("col2_val_1");
    }
    testing().waitAndAssertTraces(traceAssertConsumer(TABLE_NAME, GET, REGION_SERVER_PORT, true));
  }

  private static String value(Result result, String column) {
    return Bytes.toString(result.getValue(COLUMN_FAMILY, Bytes.toBytes(column)));
  }

  @Test
  void testGetTimeout() throws IOException {
    try (Connection timeoutConnection = ConnectionFactory.createConnection(getTimeoutConfig());
        Table table = timeoutConnection.getTable(TABLE_NAME)) {
      warmUpTimeoutConnection(table);

      assertThatExceptionOfType(IOException.class).isThrownBy(() -> getWithPausedContainer(table));
    }

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(GET + " " + TABLE_NAME.getNameAsString())
                            .hasKind(SpanKind.CLIENT)
                            .hasStatus(StatusData.error())
                            .hasAttributesSatisfyingExactly(
                                equalTo(
                                    maybeStable(DB_SYSTEM),
                                    maybeStableDbSystemName(DB_SYSTEM_VALUE)),
                                equalTo(maybeStable(DB_OPERATION), GET),
                                equalTo(
                                    maybeStable(DB_NAME),
                                    emitStableDatabaseSemconv()
                                        ? TABLE_NAME.getNamespaceAsString()
                                        : TABLE_NAME.getNameAsString()),
                                equalTo(
                                    DB_COLLECTION_NAME,
                                    emitStableDatabaseSemconv()
                                        ? TABLE_NAME.getNameAsString()
                                        : null),
                                equalTo(SERVER_ADDRESS, hostname),
                                equalTo(SERVER_PORT, REGION_SERVER_PORT),
                                equalTo(
                                    ERROR_TYPE,
                                    emitStableDatabaseSemconv()
                                        ? CallTimeoutException.class.getName()
                                        : null),
                                satisfies(
                                    DB_USER,
                                    emitStableDatabaseSemconv()
                                        ? AbstractAssert::isNull
                                        : AbstractAssert::isNotNull))
                            .hasEventsSatisfyingExactly(
                                event ->
                                    event
                                        .hasName("exception")
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(
                                                EXCEPTION_TYPE,
                                                CallTimeoutException.class.getName()),
                                            satisfies(EXCEPTION_MESSAGE, AbstractAssert::isNotNull),
                                            satisfies(
                                                EXCEPTION_STACKTRACE,
                                                AbstractAssert::isNotNull)))));
  }

  private Configuration getTimeoutConfig() {
    Configuration timeoutConfig = HBaseConfiguration.create(connection.getConfiguration());
    timeoutConfig.setInt(HConstants.HBASE_CLIENT_RETRIES_NUMBER, 0);
    timeoutConfig.setLong(HConstants.HBASE_CLIENT_PAUSE, 1);
    timeoutConfig.setInt(
        HConstants.HBASE_CLIENT_OPERATION_TIMEOUT, GET_TIMEOUT_OPERATION_TIMEOUT_MILLIS);
    timeoutConfig.setInt(HConstants.HBASE_RPC_TIMEOUT_KEY, GET_TIMEOUT_RPC_TIMEOUT_MILLIS);
    timeoutConfig.setInt(HConstants.HBASE_RPC_READ_TIMEOUT_KEY, GET_TIMEOUT_RPC_TIMEOUT_MILLIS);
    timeoutConfig.setInt(HConstants.HBASE_RPC_WRITE_TIMEOUT_KEY, GET_TIMEOUT_RPC_TIMEOUT_MILLIS);
    return timeoutConfig;
  }

  private void warmUpTimeoutConnection(Table table) throws IOException {
    table.exists(new Get(Bytes.toBytes(ROW_1)));
    testing().waitForTraces(2);
    testing().clearData();
  }

  private void getWithPausedContainer(Table table) throws IOException {
    hbaseContainer.getDockerClient().pauseContainerCmd(hbaseContainer.getContainerId()).exec();
    try {
      table.get(new Get(Bytes.toBytes(ROW_1)));
    } finally {
      hbaseContainer.getDockerClient().unpauseContainerCmd(hbaseContainer.getContainerId()).exec();
    }
  }

  @Test
  void testScan() throws IOException {
    List<String> rowIdList = new ArrayList<>();
    try (Table table = connection.getTable(TABLE_NAME)) {
      Scan scan = new Scan();
      scan.setCaching(5);
      scan.setRowPrefixFilter(Bytes.toBytes(SCAN_ROW));
      try (ResultScanner scanner = table.getScanner(scan)) {
        for (Result result : scanner) {
          rowIdList.add(Bytes.toString(result.getRow()));
        }
      }
    }
    assertThat(rowIdList).containsExactly(SCAN_ROW);
    testing().waitAndAssertTraces(traceAssertConsumer(TABLE_NAME, SCAN, REGION_SERVER_PORT, true));
  }

  @ParameterizedTest
  @MethodSource("batchScenarios")
  void testBatch(BatchScenario scenario) throws IOException, InterruptedException {
    try (Table table = connection.getTable(TABLE_NAME)) {
      table.batch(scenario.actions, new Object[scenario.actions.size()]);
    }

    // an empty batch sends nothing to the server, so no span is produced under either semconv
    if (scenario.actions.isEmpty()) {
      assertThat(testing().spans()).isEmpty();
      return;
    }

    if (!emitStableDatabaseSemconv()) {
      // old semconv reports every batch RPC as the raw "Multi" operation
      testing()
          .waitAndAssertTraces(traceAssertConsumer(TABLE_NAME, MULTI, REGION_SERVER_PORT, true));
      return;
    }

    // stable semconv derives the batch operation name and db.operation.batch.size
    testing()
        .waitAndAssertTraces(batchTraceAssertConsumer(scenario.operationName, scenario.batchSize));
  }

  private static Stream<Arguments> batchScenarios() {
    return Stream.of(
        // an empty batch sends nothing to the server, so it produces no span
        argumentSet("empty", BatchScenario.builder().build()),
        // a single operation is modeled as a non-batch operation (no db.operation.batch.size)
        argumentSet(
            "single", BatchScenario.builder().addAction(get(ROW_1)).operationName(GET).build()),
        argumentSet(
            "twoGets",
            BatchScenario.builder()
                .addAction(get(ROW_1))
                .addAction(get(ROW_2))
                .operationName("BATCH " + GET)
                .batchSize(2L)
                .build()),
        argumentSet(
            "twoMutations",
            BatchScenario.builder()
                .addAction(put("batch-mutation-row-1"))
                .addAction(put("batch-mutation-row-2"))
                .operationName("BATCH " + MUTATE)
                .batchSize(2L)
                .build()),
        argumentSet(
            "mixed",
            BatchScenario.builder()
                .addAction(put("batch-mixed-put-row"))
                .addAction(get(ROW_1))
                .operationName("BATCH")
                .batchSize(2L)
                .build()));
  }

  private static Get get(String rowKey) {
    return new Get(Bytes.toBytes(rowKey));
  }

  private static Put put(String rowKey) {
    Put put = new Put(Bytes.toBytes(rowKey));
    put.addColumn(COLUMN_FAMILY, Bytes.toBytes("col1"), Bytes.toBytes("col1_val"));
    return put;
  }

  @Test
  void testDelete() throws IOException {
    try (Table table = connection.getTable(TABLE_NAME)) {
      table.delete(new Delete(Bytes.toBytes(ROW_4)));
    }
    testing()
        .waitAndAssertTraces(traceAssertConsumer(TABLE_NAME, MUTATE, REGION_SERVER_PORT, true));
  }

  @Test
  void testAppend() throws IOException {
    try (Table table = connection.getTable(TABLE_NAME)) {
      Append append = new Append(Bytes.toBytes("append-row"));
      append.add(COLUMN_FAMILY, Bytes.toBytes("col3"), Bytes.toBytes(1L));
      table.append(append);
    }
    testing()
        .waitAndAssertTraces(traceAssertConsumer(TABLE_NAME, MUTATE, REGION_SERVER_PORT, true));
  }

  @Test
  void testIncrement() throws IOException {
    try (Table table = connection.getTable(TABLE_NAME)) {
      Increment increment = new Increment(Bytes.toBytes("increment-row"));
      increment.addColumn(COLUMN_FAMILY, Bytes.toBytes("col3"), 1L);
      table.increment(increment);
    }
    testing()
        .waitAndAssertTraces(traceAssertConsumer(TABLE_NAME, MUTATE, REGION_SERVER_PORT, true));
  }

  @Test
  void testCheckAndPutSuccess() throws IOException {
    boolean success;
    try (Table table = connection.getTable(TABLE_NAME)) {
      byte[] rowKey = Bytes.toBytes(ROW_1);
      Put put = new Put(rowKey);
      put.addColumn(COLUMN_FAMILY, Bytes.toBytes("col4"), Bytes.toBytes("new_value"));
      success =
          table.checkAndPut(
              rowKey, COLUMN_FAMILY, Bytes.toBytes("col1"), Bytes.toBytes("col1_val_1"), put);
    }
    assertThat(success).isTrue();
    testing()
        .waitAndAssertTraces(traceAssertConsumer(TABLE_NAME, MUTATE, REGION_SERVER_PORT, true));
  }

  @Test
  void testCheckAndPutFail() throws IOException {
    boolean success;
    try (Table table = connection.getTable(TABLE_NAME)) {
      byte[] rowKey = Bytes.toBytes(ROW_1);
      Put put = new Put(rowKey);
      put.addColumn(COLUMN_FAMILY, Bytes.toBytes("col5"), Bytes.toBytes("new_value"));
      success =
          table.checkAndPut(
              rowKey, COLUMN_FAMILY, Bytes.toBytes("col1"), Bytes.toBytes("expected_value"), put);
    }
    assertThat(success).isFalse();
    testing()
        .waitAndAssertTraces(traceAssertConsumer(TABLE_NAME, MUTATE, REGION_SERVER_PORT, true));
  }

  @Test
  void testCheckAndMutateSuccess() throws IOException {
    try (Table table = connection.getTable(TABLE_NAME)) {
      byte[] rowKey = Bytes.toBytes(ROW_1);
      Put put = new Put(Bytes.toBytes(ROW_3));
      put.addColumn(COLUMN_FAMILY, Bytes.toBytes("col4"), Bytes.toBytes("new_value1"));
      put.addColumn(COLUMN_FAMILY, Bytes.toBytes("col5"), Bytes.toBytes("new_value2"));

      RowMutations rowMutations = new RowMutations(Bytes.toBytes(ROW_3));
      rowMutations.add(put);
      Delete delete = new Delete(Bytes.toBytes(ROW_3));
      delete.addColumns(COLUMN_FAMILY, Bytes.toBytes("col1"));
      rowMutations.add(delete);

      table
          .checkAndMutate(rowKey, COLUMN_FAMILY)
          .qualifier(Bytes.toBytes("col1"))
          .ifMatches(CompareOperator.EQUAL, Bytes.toBytes("col1_val_1"))
          .thenMutate(rowMutations);

      Result result = table.get(new Get(Bytes.toBytes(ROW_3)));
      assertThat(value(result, "col4")).isEqualTo("new_value1");
      assertThat(value(result, "col5")).isEqualTo("new_value2");
      assertThat(result.getValue(COLUMN_FAMILY, Bytes.toBytes("col1"))).isNull();
    }
    testing()
        .waitAndAssertTraces(
            traceAssertConsumer(TABLE_NAME, MULTI, REGION_SERVER_PORT, true),
            traceAssertConsumer(TABLE_NAME, GET, REGION_SERVER_PORT, true));
  }

  @Test
  void testCheckAndDeleteSuccess() throws IOException {
    boolean success;
    try (Table table = connection.getTable(TABLE_NAME)) {
      byte[] rowKey = Bytes.toBytes(ROW_1);
      Delete delete = new Delete(rowKey);
      delete.addColumn(COLUMN_FAMILY, Bytes.toBytes("col4"));
      success =
          table.checkAndDelete(
              rowKey, COLUMN_FAMILY, Bytes.toBytes("col1"), Bytes.toBytes("col1_val_1"), delete);
    }
    assertThat(success).isTrue();
    testing()
        .waitAndAssertTraces(traceAssertConsumer(TABLE_NAME, MUTATE, REGION_SERVER_PORT, true));
  }

  @Test
  void hasDurationMetric() throws IOException {
    try (Table table = connection.getTable(TABLE_NAME)) {
      table.get(new Get(Bytes.toBytes(ROW_1)));
    }
    testing().waitForTraces(1);
    assertDurationMetric(
        testing(),
        INSTRUMENTATION_NAME,
        DB_SYSTEM_NAME,
        maybeStable(DB_OPERATION),
        maybeStable(DB_NAME),
        DB_COLLECTION_NAME,
        SERVER_ADDRESS,
        SERVER_PORT);
  }

  protected Consumer<TraceAssert> traceAssertConsumer(
      TableName table, String operation, int port, boolean hasTable) {
    String spanName;
    if (hasTable) {
      spanName = operation + " " + table.getNameAsString();
    } else if (emitStableDatabaseSemconv()) {
      spanName = operation + " " + hostname + ":" + port;
    } else {
      spanName = operation;
    }
    return trace ->
        trace.hasSpansSatisfyingExactly(
            span ->
                span.hasName(spanName)
                    .hasKind(SpanKind.CLIENT)
                    .hasAttributesSatisfyingExactly(
                        equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName(DB_SYSTEM_VALUE)),
                        equalTo(maybeStable(DB_OPERATION), operation),
                        equalTo(maybeStable(DB_NAME), dbNamespace(table, hasTable)),
                        equalTo(DB_COLLECTION_NAME, dbCollectionName(table, hasTable)),
                        equalTo(SERVER_ADDRESS, hostname),
                        equalTo(SERVER_PORT, port),
                        satisfies(
                            DB_USER,
                            emitStableDatabaseSemconv()
                                ? AbstractAssert::isNull
                                : AbstractAssert::isNotNull)));
  }

  private static String dbNamespace(TableName table, boolean hasTable) {
    if (!hasTable) {
      return null;
    }
    if (emitStableDatabaseSemconv()) {
      return table.getNamespaceAsString();
    }
    return table.getNameAsString();
  }

  private static String dbCollectionName(TableName table, boolean hasTable) {
    if (hasTable && emitStableDatabaseSemconv()) {
      return table.getNameAsString();
    }
    return null;
  }

  // Asserts a stable-semconv batch span. db.operation.batch.size is present only for multi-action
  // batches; a single-action batch is modeled as a non-batch operation, so batchSize is null and
  // the attribute is asserted absent.
  private Consumer<TraceAssert> batchTraceAssertConsumer(String operation, Long batchSize) {
    return trace ->
        trace.hasSpansSatisfyingExactly(
            span ->
                span.hasName(operation + " " + TABLE_NAME.getNameAsString())
                    .hasKind(SpanKind.CLIENT)
                    .hasAttributesSatisfyingExactly(
                        equalTo(DB_SYSTEM_NAME, maybeStableDbSystemName(DB_SYSTEM_VALUE)),
                        equalTo(DB_NAMESPACE, TABLE_NAME.getNamespaceAsString()),
                        equalTo(DB_COLLECTION_NAME, TABLE_NAME.getNameAsString()),
                        equalTo(DB_OPERATION_NAME, operation),
                        equalTo(DB_OPERATION_BATCH_SIZE, batchSize),
                        equalTo(SERVER_ADDRESS, hostname),
                        equalTo(SERVER_PORT, REGION_SERVER_PORT)));
  }

  private static class BatchScenario {
    final List<Row> actions;
    final String operationName;
    final Long batchSize;

    BatchScenario(Builder builder) {
      this.actions = builder.actions;
      this.operationName = builder.operationName;
      this.batchSize = builder.batchSize;
    }

    static Builder builder() {
      return new Builder();
    }

    static class Builder {
      private final List<Row> actions = new ArrayList<>();
      private String operationName;
      private Long batchSize;

      Builder operationName(String operationName) {
        this.operationName = operationName;
        return this;
      }

      Builder batchSize(Long batchSize) {
        this.batchSize = batchSize;
        return this;
      }

      Builder addAction(Row action) {
        this.actions.add(action);
        return this;
      }

      BatchScenario build() {
        return new BatchScenario(this);
      }
    }
  }
}
