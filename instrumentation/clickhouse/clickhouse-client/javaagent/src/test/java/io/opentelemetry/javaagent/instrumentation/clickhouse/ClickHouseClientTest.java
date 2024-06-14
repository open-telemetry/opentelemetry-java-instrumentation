/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Arrays.asList;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.clickhouse.client.ClickHouseClient;
import com.clickhouse.client.ClickHouseConfig;
import com.clickhouse.client.ClickHouseException;
import com.clickhouse.client.ClickHouseNode;
import com.clickhouse.client.ClickHouseResponse;
import com.clickhouse.client.ClickHouseResponseSummary;
import com.clickhouse.client.config.ClickHouseClientOption;
import com.clickhouse.config.ClickHouseOption;
import com.clickhouse.data.ClickHouseFormat;
import com.clickhouse.data.ClickHouseRecord;
import com.clickhouse.data.ClickHouseValue;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

@TestInstance(Lifecycle.PER_CLASS)
class ClickHouseClientTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final GenericContainer<?> clickhouseServer =
      new GenericContainer<>("clickhouse/clickhouse-server:24.4.2").withExposedPorts(8123);

  private static int port;
  private static final String dbName = "default";
  private static String host;

  private static ClickHouseNode server;
  private static ClickHouseClient client;

  @BeforeAll
  void setup() throws ClickHouseException {
    clickhouseServer.start();
    port = clickhouseServer.getMappedPort(8123);
    host = clickhouseServer.getHost();
    server = ClickHouseNode.of("http://" + host + ":" + port + "/" + dbName);

    Map<ClickHouseOption, Serializable> options = new HashMap<>();
    options.put(ClickHouseClientOption.COMPRESS, false);

    client = ClickHouseClient.builder().config(new ClickHouseConfig(options)).build();

    client.ping(server, 10000);
    client.read(server).query("drop table if exists test_table").executeAndWait();
  }

  @AfterAll
  void cleanup() {
    if (client != null) {
      client.close();
    }
    clickhouseServer.stop();
  }

  @Test
  void testQuery() throws ClickHouseException {

    client
        .read(server)
        .query("create table if not exists test_table(s String) engine=Memory")
        .executeAndWait();

    List<String> tablesNames = new ArrayList<>();

    ClickHouseResponse showTablesResponse =
        client.read(server).query("SHOW TABLES").executeAndWait();

    for (ClickHouseRecord r : showTablesResponse.records()) {
      ClickHouseValue v = r.getValue(0);
      String tableName = v.asString();
      tablesNames.add(tableName);
    }

    assertThat(tablesNames.size()).isEqualTo(1);
    assertThat(tablesNames.get(0)).isEqualTo("test_table");

    client.read(server).query("insert into test_table values('1')('2')('3')").executeAndWait();

    assertThat(tablesNames.size()).isEqualTo(1);

    ClickHouseResponse response =
        client
            .read(server)
            .format(ClickHouseFormat.RowBinaryWithNamesAndTypes)
            .query("select * from test_table")
            .executeAndWait();

    ClickHouseResponseSummary summary = response.getSummary();
    assertThat(summary).isNotNull();
    assertThat(summary.getProgress().getReadRows()).isEqualTo(3);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("CREATE table" + dbName)
                        .hasNoParent()
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            attributeAssertions(
                                "create table if not exists test_table(s String) engine=Memory",
                                "CREATE table"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SHOW TABLES " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            attributeAssertions("SHOW TABLES", "SHOW TABLES"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("INSERT " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            attributeAssertions(
                                "insert into test_table values(?)(?)(?)", "INSERT"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SELECT " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            attributeAssertions("select * from test_table", "SELECT"))));
  }

  private static List<AttributeAssertion> attributeAssertions(String statement, String operation) {
    return asList(
        equalTo(DbIncubatingAttributes.DB_SYSTEM, "clickhouse"),
        equalTo(DbIncubatingAttributes.DB_NAME, dbName),
        equalTo(ServerAttributes.SERVER_ADDRESS, host),
        equalTo(ServerAttributes.SERVER_PORT, port),
        equalTo(DbIncubatingAttributes.DB_STATEMENT, statement),
        equalTo(DbIncubatingAttributes.DB_OPERATION, operation));
  }
  //
  //  @Test
  //  void testQueryAndModifyWithOneArgument() {
  //    String dbName = databaseName + "2";
  //    influxDb.createDatabase(dbName);
  //    BatchPoints batchPoints =
  //        BatchPoints.database(dbName).tag("async", "true").retentionPolicy("autogen").build();
  //    Point point1 =
  //        Point.measurement("cpu")
  //            .tag("atag", "test")
  //            .addField("idle", 90L)
  //            .addField("usertime", 9L)
  //            .addField("system", 1L)
  //            .build();
  //    Point point2 =
  //        Point.measurement("disk")
  //            .tag("atag", "test")
  //            .addField("used", 80L)
  //            .addField("free", 1L)
  //            .build();
  //    batchPoints.point(point1);
  //    batchPoints.point(point2);
  //    influxDb.write(batchPoints);
  //    Query query = new Query("SELECT * FROM cpu GROUP BY *", dbName);
  //    QueryResult result = influxDb.query(query);
  //    assertThat(result.getResults().get(0).getSeries().get(0).getTags()).isNotEmpty();
  //    influxDb.deleteDatabase(dbName);
  //    testing.waitAndAssertTraces(
  //        trace ->
  //            trace.hasSpansSatisfyingExactly(
  //                span ->
  //                    span.hasName("CREATE DATABASE " + dbName)
  //                        .hasKind(SpanKind.CLIENT)
  //                        .hasAttributesSatisfying(
  //                            attributeAssertions(
  //                                String.format(CREATE_DATABASE_STATEMENT_NEW, dbName),
  //                                "CREATE DATABASE",
  //                                dbName))),
  //        trace ->
  //            trace.hasSpansSatisfyingExactly(
  //                span ->
  //                    span.hasName("write " + dbName)
  //                        .hasKind(SpanKind.CLIENT)
  //                        .hasAttributesSatisfying(attributeAssertions("write", "write",
  // dbName))),
  //        trace ->
  //            trace.hasSpansSatisfyingExactly(
  //                span ->
  //                    span.hasName("SELECT " + dbName)
  //                        .hasKind(SpanKind.CLIENT)
  //                        .hasAttributesSatisfying(
  //                            attributeAssertions("SELECT * FROM cpu GROUP BY *", "SELECT",
  // dbName))),
  //        trace ->
  //            trace.hasSpansSatisfyingExactly(
  //                span ->
  //                    span.hasName("DROP DATABASE " + dbName)
  //                        .hasKind(SpanKind.CLIENT)
  //                        .hasAttributesSatisfying(
  //                            attributeAssertions(
  //                                String.format(DELETE_DATABASE_STATEMENT, dbName),
  //                                "DROP DATABASE",
  //                                dbName))));
  //  }
  //
  //  @Test
  //  void testQueryWithTwoArguments() {
  //    Query query = new Query("SELECT * FROM cpu_load where test1 = 'influxDb'", databaseName);
  //    influxDb.query(query, TimeUnit.MILLISECONDS);
  //
  //    testing.waitAndAssertTraces(
  //        trace ->
  //            trace.hasSpansSatisfyingExactly(
  //                span ->
  //                    span.hasName("SELECT " + databaseName)
  //                        .hasKind(SpanKind.CLIENT)
  //                        .hasAttributesSatisfying(
  //                            attributeAssertions(
  //                                "SELECT * FROM cpu_load where test1 = ?",
  //                                "SELECT",
  //                                databaseName))));
  //  }
  //
  //  @Test
  //  void testQueryWithThreeArguments() throws InterruptedException {
  //    Query query =
  //        new Query(
  //            "SELECT * FROM cpu_load where time >= '2022-01-01T08:00:00Z' AND time <=
  // '2022-01-01T20:00:00Z'",
  //            databaseName);
  //    BlockingQueue<QueryResult> queue = new LinkedBlockingQueue<>();
  //
  //    influxDb.query(query, 2, result -> queue.add(result));
  //    queue.poll(20, TimeUnit.SECONDS);
  //
  //    testing.waitAndAssertTraces(
  //        trace ->
  //            trace.hasSpansSatisfyingExactly(
  //                span ->
  //                    span.hasName("SELECT " + databaseName)
  //                        .hasKind(SpanKind.CLIENT)
  //                        .hasAttributesSatisfying(
  //                            attributeAssertions(
  //                                "SELECT * FROM cpu_load where time >= ? AND time <= ?",
  //                                "SELECT",
  //                                databaseName))));
  //  }
  //
  //  @Test
  //  void testQueryWithThreeArgumentsCallback() throws InterruptedException {
  //    Query query = new Query("SELECT * FROM cpu_load", databaseName);
  //    BlockingQueue<QueryResult> queue = new LinkedBlockingQueue<>();
  //
  //    influxDb.query(query, 2, result -> queue.add(result));
  //    queue.poll(20, TimeUnit.SECONDS);
  //
  //    testing.waitAndAssertTraces(
  //        trace ->
  //            trace.hasSpansSatisfyingExactly(
  //                span ->
  //                    span.hasName("SELECT " + databaseName)
  //                        .hasKind(SpanKind.CLIENT)
  //                        .hasAttributesSatisfying(
  //                            attributeAssertions(
  //                                "SELECT * FROM cpu_load", "SELECT", databaseName))));
  //  }
  //
  //  @Test
  //  void testQueryWithFiveArguments() throws InterruptedException {
  //    CountDownLatch countDownLatch = new CountDownLatch(1);
  //    Query query =
  //        new Query(
  //            "SELECT MEAN(water_level) FROM h2o_feet where time = '2022-01-01T08:00:00Z'; SELECT
  // water_level FROM h2o_feet LIMIT 2",
  //            databaseName);
  //    testing.runWithSpan(
  //        "parent",
  //        () -> {
  //          influxDb.query(
  //              query,
  //              10,
  //              (cancellable, queryResult) -> countDownLatch.countDown(),
  //              () -> testing.runWithSpan("child", () -> {}),
  //              throwable -> {});
  //        });
  //    assertThat(countDownLatch.await(10, TimeUnit.SECONDS)).isTrue();
  //
  //    testing.waitAndAssertTraces(
  //        trace ->
  //            trace.hasSpansSatisfyingExactly(
  //                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
  //                span ->
  //                    span.hasName("SELECT " + databaseName)
  //                        .hasKind(SpanKind.CLIENT)
  //                        .hasParent(trace.getSpan(0))
  //                        .hasAttributesSatisfying(
  //                            attributeAssertions(
  //                                "SELECT MEAN(water_level) FROM h2o_feet where time = ?; SELECT
  // water_level FROM h2o_feet LIMIT ?",
  //                                "SELECT",
  //                                databaseName)),
  //                span ->
  //
  // span.hasName("child").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0))));
  //  }
  //
  //  @Test
  //  void testQueryFailedWithFiveArguments() throws InterruptedException {
  //    CountDownLatch countDownLatchFailure = new CountDownLatch(1);
  //    Query query = new Query("SELECT MEAN(water_level) FROM;", databaseName);
  //    testing.runWithSpan(
  //        "parent",
  //        () -> {
  //          influxDb.query(
  //              query,
  //              10,
  //              (cancellable, queryResult) -> {},
  //              () -> {},
  //              throwable -> {
  //                testing.runWithSpan("child", () -> {});
  //                countDownLatchFailure.countDown();
  //              });
  //        });
  //    assertThat(countDownLatchFailure.await(10, TimeUnit.SECONDS)).isTrue();
  //
  //    testing.waitAndAssertTraces(
  //        trace ->
  //            trace.hasSpansSatisfyingExactly(
  //                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
  //                span ->
  //                    span.hasName("SELECT " + databaseName)
  //                        .hasKind(SpanKind.CLIENT)
  //                        .hasParent(trace.getSpan(0))
  //                        .hasAttributesSatisfying(
  //                            attributeAssertions(
  //                                "SELECT MEAN(water_level) FROM;", "SELECT", databaseName)),
  //                span ->
  //
  // span.hasName("child").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0))));
  //  }
  //
  //  @Test
  //  void testWriteWithFourArguments() {
  //    String measurement = "cpu_load";
  //    List<String> records = new ArrayList<>();
  //    records.add(measurement + ",atag=test1 idle=100,usertime=10,system=1 1485273600");
  //    influxDb.write(databaseName, "autogen", InfluxDB.ConsistencyLevel.ONE, records);
  //
  //    testing.waitAndAssertTraces(
  //        trace ->
  //            trace.hasSpansSatisfyingExactly(
  //                span ->
  //                    span.hasName("write " + databaseName)
  //                        .hasKind(SpanKind.CLIENT)
  //                        .hasAttributesSatisfying(
  //                            attributeAssertions("write", "write", databaseName))));
  //  }
  //
  //  @Test
  //  void testWriteWithFiveArguments() {
  //    String measurement = "cpu_load";
  //    List<String> records = new ArrayList<>();
  //    records.add(measurement + ",atag=test1 idle=100,usertime=10,system=1 1485273600");
  //    influxDb.write(
  //        databaseName, "autogen", InfluxDB.ConsistencyLevel.ONE, TimeUnit.SECONDS, records);
  //
  //    testing.waitAndAssertTraces(
  //        trace ->
  //            trace.hasSpansSatisfyingExactly(
  //                span ->
  //                    span.hasName("write " + databaseName)
  //                        .hasKind(SpanKind.CLIENT)
  //                        .hasAttributesSatisfying(
  //                            attributeAssertions("write", "write", databaseName))));
  //  }
  //
  //  @Test
  //  void testWriteWithUdp() {
  //    List<String> lineProtocols = new ArrayList<>();
  //    for (int i = 0; i < 2000; i++) {
  //      Point point = Point.measurement("udp_single_poit").addField("v", i).build();
  //      lineProtocols.add(point.lineProtocol());
  //    }
  //    influxDb.write(port, lineProtocols);
  //
  //    testing.waitAndAssertTraces(
  //        trace ->
  //            trace.hasSpansSatisfyingExactly(
  //                span ->
  //                    span.hasName("write")
  //                        .hasKind(SpanKind.CLIENT)
  //                        .hasAttributesSatisfying(attributeAssertions("write", "write", null))));
  //  }
  //
  //  private static List<AttributeAssertion> attributeAssertions(
  //      String statement, String operation, String databaseName) {
  //    return asList(
  //        equalTo(DbIncubatingAttributes.DB_SYSTEM, "influxdb"),
  //        equalTo(DbIncubatingAttributes.DB_NAME, databaseName),
  //        equalTo(ServerAttributes.SERVER_ADDRESS, host),
  //        equalTo(ServerAttributes.SERVER_PORT, port),
  //        equalTo(DbIncubatingAttributes.DB_STATEMENT, statement),
  //        equalTo(DbIncubatingAttributes.DB_OPERATION, operation));
  //  }
}
