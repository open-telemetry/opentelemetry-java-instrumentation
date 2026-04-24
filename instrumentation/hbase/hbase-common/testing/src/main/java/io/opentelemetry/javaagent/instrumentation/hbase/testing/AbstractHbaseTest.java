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
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_USER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.CompareOperator;
import org.apache.hadoop.hbase.HBaseConfiguration;
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
import org.apache.hadoop.hbase.client.RowMutations;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.util.Bytes;
import org.assertj.core.api.AbstractAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@SuppressWarnings("deprecation")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractHbaseTest {

  protected static final int MASTER_PORT = 16000;
  protected static final int REGION_SERVER_PORT = 16020;
  protected static final String INSTRUMENTATION_NAME = "io.opentelemetry.hbase-client-2.0.0";

  private static final String NAMESPACE = "ot_test";
  protected static final byte[] COLUMN_FAMILY = Bytes.toBytes("cf");
  protected static final TableName TABLE_NAME = TableName.valueOf("ot_test:eleven_test_table");
  protected static final TableName META = TableName.valueOf("hbase:meta");

  private static final String DB_SYSTEM_VALUE = "hbase";
  protected static final String SCAN = "Scan";
  protected static final String MUTATE = "Mutate";
  protected static final String GET = "Get";
  protected static final String MULTI = "Multi";

  protected static final String HOSTNAME = getHostName();

  protected abstract InstrumentationExtension testing();

  protected static final GenericContainer<?> hbaseContainer =
      new GenericContainer<>(DockerImageName.parse("harisekhon/hbase:2.0"))
          .withCreateContainerCmdModifier(
              cmd -> {
                cmd.getHostConfig()
                    .withPortBindings(
                        new PortBinding(Ports.Binding.bindPort(2181), new ExposedPort(2181)),
                        new PortBinding(Ports.Binding.bindPort(16000), new ExposedPort(16000)),
                        new PortBinding(Ports.Binding.bindPort(16010), new ExposedPort(16010)),
                        new PortBinding(Ports.Binding.bindPort(16020), new ExposedPort(16020)),
                        new PortBinding(Ports.Binding.bindPort(16030), new ExposedPort(16030)),
                        new PortBinding(Ports.Binding.bindPort(16201), new ExposedPort(16201)),
                        new PortBinding(Ports.Binding.bindPort(16301), new ExposedPort(16301)));
              })
          .withExposedPorts(2181, 16000, 16010, 16020, 16030, 16201, 16301)
          .withStartupTimeout(Duration.ofMinutes(2))
          .waitingFor(Wait.forHealthcheck().withStartupTimeout(Duration.ofMinutes(2)))
          .withCreateContainerCmdModifier(cmd -> cmd.withHostName(HOSTNAME))
          .waitingFor(Wait.forListeningPort())
          .waitingFor(Wait.forLogMessage(".*Master has completed initialization.*\\n", 1));

  protected static Connection connection;

  private static String getHostName() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      return null;
    }
  }

  @BeforeAll
  static void setupSpec() throws IOException {
    hbaseContainer.start();
    String host = hbaseContainer.getHost();
    Configuration config = HBaseConfiguration.create();
    config.set("hbase.zookeeper.quorum", host);
    config.set("hbase.zookeeper.property.clientPort", "2181");
    connection = ConnectionFactory.createConnection(config);
  }

  @AfterAll
  static void cleanupSpec() throws IOException {
    hbaseContainer.stop();
    connection.close();
  }

  @BeforeEach
  void setup() {
    testing().clearData();
  }

  @Test
  @Order(1)
  public void testCreateNameBase() {
    Exception error = null;
    boolean namespaceCreateStatus;
    try {
      Admin admin = connection.getAdmin();
      NamespaceDescriptor namespaceDescriptor = NamespaceDescriptor.create(NAMESPACE).build();
      admin.createNamespace(namespaceDescriptor);
      namespaceCreateStatus = true;
      admin.close();
    } catch (IOException e) {
      namespaceCreateStatus = false;
      error = e;
    }
    assertNull(error);
    assertTrue(namespaceCreateStatus);
  }

  @Test
  @Order(2)
  public void testListNamespace() {
    Exception error = null;
    boolean namespaceExists = false;
    try {
      Admin admin = connection.getAdmin();
      for (NamespaceDescriptor ns : admin.listNamespaceDescriptors()) {
        if (ns.getName().equals(NAMESPACE)) {
          namespaceExists = true;
        }
      }
      admin.close();
    } catch (IOException e) {
      error = e;
    }
    assertNull(error);
    assertTrue(namespaceExists);
    testing()
        .waitAndAssertTraces(
            traceAssertConsumer(null, "IsMasterRunning", MASTER_PORT, false),
            traceAssertConsumer(null, "ListNamespaceDescriptors", MASTER_PORT, false));
  }

  @Test
  @Order(3)
  public void testCreateTable() {
    Exception error = null;
    boolean tableExists = false;
    try {
      Admin admin = connection.getAdmin();
      ColumnFamilyDescriptor columnFamilyDescriptor =
          ColumnFamilyDescriptorBuilder.newBuilder(COLUMN_FAMILY).build();
      TableDescriptor tableDescriptor =
          TableDescriptorBuilder.newBuilder(TABLE_NAME)
              .setColumnFamily(columnFamilyDescriptor)
              .build();
      admin.createTable(tableDescriptor);
      tableExists = true;
    } catch (IOException e) {
      tableExists = false;
      error = e;
    }
    assertNull(error);
    assertTrue(tableExists);
  }

  @Test
  public void testListTable() {
    Exception error = null;
    boolean tableExists = false;
    try {
      Admin admin = connection.getAdmin();
      for (TableName tableName : admin.listTableNames()) {
        if (tableName.equals(TABLE_NAME)) {
          tableExists = true;
        }
      }
      admin.close();
    } catch (IOException e) {
      error = e;
    }
    assertNull(error);
    assertTrue(tableExists);
    testing()
        .waitAndAssertTraces(
            traceAssertConsumer(null, "IsMasterRunning", MASTER_PORT, false),
            traceAssertConsumer(null, "GetTableNames", MASTER_PORT, false));
  }

  @Test
  @Order(4)
  public void testPut() {
    Integer id = 1;
    Exception error = null;
    try (Table table = connection.getTable(TABLE_NAME)) {
      Put put = new Put(Bytes.toBytes("row" + id));
      put.addColumn(COLUMN_FAMILY, Bytes.toBytes("col1"), Bytes.toBytes("col1_val_" + id));
      put.addColumn(COLUMN_FAMILY, Bytes.toBytes("col2"), Bytes.toBytes("col2_val_" + id));
      table.put(put);
    } catch (Exception e) {
      error = e;
    }
    assertNull(error);
    testing()
        .waitAndAssertTraces(
            traceAssertConsumer(META, SCAN, REGION_SERVER_PORT, true),
            traceAssertConsumer(TABLE_NAME, MUTATE, REGION_SERVER_PORT, true));
  }

  @Test
  @Order(100)
  public void testGet() {
    Exception error = null;
    String col1Val = null;
    String col2Val = null;
    try (Table table = connection.getTable(TABLE_NAME)) {
      Get get = new Get(Bytes.toBytes("row1"));
      Result result = table.get(get);
      col1Val = Bytes.toString(result.getValue(COLUMN_FAMILY, Bytes.toBytes("col1")));
      col2Val = Bytes.toString(result.getValue(COLUMN_FAMILY, Bytes.toBytes("col2")));
    } catch (Exception e) {
      error = e;
    }
    assertNull(error);
    assertEquals(col1Val, "col1_val_1");
    assertEquals(col2Val, "col2_val_1");
    testing().waitAndAssertTraces(traceAssertConsumer(TABLE_NAME, GET, REGION_SERVER_PORT, true));
  }

  @Test
  @Order(100)
  public void testScan() {
    Exception error = null;
    List<String> rowIdList = new ArrayList<>();
    try (Table table = connection.getTable(TABLE_NAME)) {
      Scan scan = new Scan();
      scan.setCaching(5);
      ResultScanner scanner = table.getScanner(scan);
      for (Result result : scanner) {
        rowIdList.add(Bytes.toString(result.getRow()));
      }
      scanner.close();
    } catch (Exception e) {
      error = e;
    }
    assertNull(error);
    assertEquals(rowIdList.size(), 1);
    assertEquals(rowIdList.get(0), "row1");
    testing().waitAndAssertTraces(traceAssertConsumer(TABLE_NAME, SCAN, REGION_SERVER_PORT, true));
  }

  @Test
  @Order(100)
  public void testBatchGet() {
    Exception error = null;
    Result[] results = null;
    try (Table table = connection.getTable(TABLE_NAME)) {
      List<Get> getList = new ArrayList<>();
      getList.add(new Get(Bytes.toBytes("row1")));
      getList.add(new Get(Bytes.toBytes("row2")));
      getList.add(new Get(Bytes.toBytes("row5")));
      results = table.get(getList);
    } catch (Exception e) {
      error = e;
    }
    assertNull(error);
    assertNotNull(results);
    assertEquals(3, results.length);
    {
      assertEquals("row1", Bytes.toString(results[0].getRow()));
      assertEquals(
          "col1_val_1", Bytes.toString(results[0].getValue(COLUMN_FAMILY, Bytes.toBytes("col1"))));
      assertEquals(
          "col2_val_1", Bytes.toString(results[0].getValue(COLUMN_FAMILY, Bytes.toBytes("col2"))));
    }
    {
      assertNull(Bytes.toString(results[1].getRow()));
      assertNull(Bytes.toString(results[1].getValue(COLUMN_FAMILY, Bytes.toBytes("col1"))));
      assertNull(Bytes.toString(results[1].getValue(COLUMN_FAMILY, Bytes.toBytes("col2"))));
    }
    {
      assertNull(Bytes.toString(results[2].getRow()));
      assertNull(Bytes.toString(results[2].getValue(COLUMN_FAMILY, Bytes.toBytes("col1"))));
      assertNull(Bytes.toString(results[2].getValue(COLUMN_FAMILY, Bytes.toBytes("col2"))));
    }
    testing().waitAndAssertTraces(traceAssertConsumer(TABLE_NAME, MULTI, REGION_SERVER_PORT, true));
  }

  @Test
  @Order(200)
  public void testBatchPut() {
    Exception error = null;
    try (Table table = connection.getTable(TABLE_NAME)) {
      List<Put> putList = new ArrayList<>();
      for (int i = 2; i < 5; i++) {
        Put put = new Put(Bytes.toBytes("row" + i));
        put.addColumn(COLUMN_FAMILY, Bytes.toBytes("col1"), Bytes.toBytes("col1_val_" + i));
        putList.add(put);
      }
      table.put(putList);
    } catch (Exception e) {
      error = e;
    }
    assertNull(error);
    testing().waitAndAssertTraces(traceAssertConsumer(TABLE_NAME, MULTI, REGION_SERVER_PORT, true));
  }

  @Test
  @Order(201)
  public void testDelete() {
    Exception error = null;
    try (Table table = connection.getTable(TABLE_NAME)) {
      table.delete(new Delete(Bytes.toBytes("row4")));
    } catch (Exception e) {
      error = e;
    }
    assertNull(error);
    testing()
        .waitAndAssertTraces(traceAssertConsumer(TABLE_NAME, MUTATE, REGION_SERVER_PORT, true));
  }

  @Test
  @Order(300)
  public void testAppend() {
    Exception error = null;
    try (Table table = connection.getTable(TABLE_NAME)) {
      Append append = new Append(Bytes.toBytes("row2"));
      append.add(COLUMN_FAMILY, Bytes.toBytes("col3"), Bytes.toBytes(1L));
      table.append(append);
    } catch (Exception e) {
      error = e;
    }
    assertNull(error);
    testing()
        .waitAndAssertTraces(traceAssertConsumer(TABLE_NAME, MUTATE, REGION_SERVER_PORT, true));
  }

  @Test
  @Order(301)
  public void testIncrement() {
    Exception error = null;
    try (Table table = connection.getTable(TABLE_NAME)) {
      Increment increment = new Increment(Bytes.toBytes("row2"));
      increment.addColumn(COLUMN_FAMILY, Bytes.toBytes("col3"), 1L);
      table.increment(increment);
    } catch (Exception e) {
      error = e;
    }
    assertNull(error);
    testing()
        .waitAndAssertTraces(traceAssertConsumer(TABLE_NAME, MUTATE, REGION_SERVER_PORT, true));
  }

  @Test
  @Order(400)
  public void testCheckAndPutSuccess() {
    Exception error = null;
    boolean success = false;
    try (Table table = connection.getTable(TABLE_NAME)) {
      byte[] rowKey = Bytes.toBytes("row1");
      Put put = new Put(rowKey);
      put.addColumn(COLUMN_FAMILY, Bytes.toBytes("col4"), Bytes.toBytes("new_value"));
      success =
          table.checkAndPut(
              rowKey, COLUMN_FAMILY, Bytes.toBytes("col1"), Bytes.toBytes("col1_val_1"), put);
    } catch (Exception e) {
      error = e;
    }
    assertNull(error);
    assertTrue(success);
    testing()
        .waitAndAssertTraces(traceAssertConsumer(TABLE_NAME, MUTATE, REGION_SERVER_PORT, true));
  }

  @Test
  @Order(400)
  public void testCheckAndPutFail() {
    Exception error = null;
    boolean success = false;
    try (Table table = connection.getTable(TABLE_NAME)) {
      byte[] rowKey = Bytes.toBytes("row1");
      Put put = new Put(rowKey);
      put.addColumn(COLUMN_FAMILY, Bytes.toBytes("col5"), Bytes.toBytes("new_value"));
      success =
          table.checkAndPut(
              rowKey, COLUMN_FAMILY, Bytes.toBytes("col1"), Bytes.toBytes("expected_value"), put);
    } catch (Exception e) {
      error = e;
    }
    assertNull(error);
    assertFalse(success);
    testing()
        .waitAndAssertTraces(traceAssertConsumer(TABLE_NAME, MUTATE, REGION_SERVER_PORT, true));
  }

  @Test
  @Order(400)
  public void testCheckAndMutateSuccess() {
    Exception error = null;
    boolean success = false;
    try (Table table = connection.getTable(TABLE_NAME)) {
      byte[] rowKey = Bytes.toBytes("row1");
      Put put = new Put(Bytes.toBytes("row3"));
      put.addColumn(COLUMN_FAMILY, Bytes.toBytes("col4"), Bytes.toBytes("new_value"));
      put.addColumn(COLUMN_FAMILY, Bytes.toBytes("col5"), Bytes.toBytes("new_value"));

      RowMutations rowMutations = new RowMutations(Bytes.toBytes("row3"));
      rowMutations.add(put);
      Delete delete = new Delete(Bytes.toBytes("row3"));
      delete.addColumns(COLUMN_FAMILY, Bytes.toBytes("col2"));
      rowMutations.add(delete);

      success =
          table
              .checkAndMutate(rowKey, COLUMN_FAMILY)
              .qualifier(Bytes.toBytes("col1"))
              .ifMatches(CompareOperator.EQUAL, Bytes.toBytes("col1_val_1"))
              .thenMutate(rowMutations);
    } catch (Exception e) {
      error = e;
    }
    assertNull(error);
    assertTrue(success);
    testing().waitAndAssertTraces(traceAssertConsumer(TABLE_NAME, MULTI, REGION_SERVER_PORT, true));
  }

  @Test
  @Order(500)
  public void testCheckAndDeleteSuccess() {
    Exception error = null;
    boolean success = false;
    try (Table table = connection.getTable(TABLE_NAME)) {
      byte[] rowKey = Bytes.toBytes("row1");
      Delete delete = new Delete(rowKey);
      delete.addColumn(COLUMN_FAMILY, Bytes.toBytes("col4"));
      success =
          table.checkAndDelete(
              rowKey, COLUMN_FAMILY, Bytes.toBytes("col1"), Bytes.toBytes("col1_val_1"), delete);
    } catch (Exception e) {
      error = e;
    }
    assertNull(error);
    assertTrue(success);
    testing()
        .waitAndAssertTraces(traceAssertConsumer(TABLE_NAME, MUTATE, REGION_SERVER_PORT, true));
  }

  @Test
  @Order(600)
  public void hasDurationMetric() {
    Exception error = null;
    try (Table table = connection.getTable(TABLE_NAME)) {
      table.get(new Get(Bytes.toBytes("row1")));
    } catch (Exception e) {
      error = e;
    }
    assertNull(error);
    testing().waitForTraces(1);
    assertDurationMetric(
        testing(),
        INSTRUMENTATION_NAME,
        DB_SYSTEM_NAME,
        maybeStable(DB_OPERATION),
        maybeStable(DB_NAME),
        SERVER_ADDRESS,
        SERVER_PORT);
  }

  protected static Consumer<TraceAssert> traceAssertConsumer(
      TableName table, String operation, int port, boolean hasTable) {
    List<AttributeAssertion> assertions = new ArrayList<>();
    assertions.add(equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName(DB_SYSTEM_VALUE)));
    assertions.add(equalTo(maybeStable(DB_OPERATION), operation));
    if (hasTable) {
      assertions.add(equalTo(maybeStable(DB_NAME), table.getNameAsString()));
    }
    assertions.add(equalTo(SERVER_ADDRESS, HOSTNAME));
    assertions.add(equalTo(SERVER_PORT, port));
    if (!emitStableDatabaseSemconv()) {
      assertions.add(satisfies(DB_USER, AbstractAssert::isNotNull));
    }
    String spanName;
    if (hasTable) {
      spanName = operation + " " + table.getNameAsString();
    } else if (emitStableDatabaseSemconv()) {
      spanName = operation + " " + HOSTNAME + ":" + port;
    } else {
      spanName = operation;
    }
    return trace ->
        trace.hasSpansSatisfyingExactly(
            span ->
                span.hasName(spanName)
                    .hasKind(SpanKind.CLIENT)
                    .hasAttributesSatisfyingExactly(assertions));
  }
}
