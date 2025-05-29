/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

// ignore using deprecated createDatabase and deleteDatabase methods warning.
@SuppressWarnings("deprecation")
@TestInstance(Lifecycle.PER_CLASS)
class InfluxDbClientTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final GenericContainer<?> influxDbServer =
      new GenericContainer<>("influxdb:1.8.10-alpine").withExposedPorts(8086);

  private static InfluxDB influxDb;

  private static final String databaseName = "mydb";

  private static String host;

  private static int port;

  @BeforeAll
  void setup() {
    influxDbServer.start();
    port = influxDbServer.getMappedPort(8086);
    host = influxDbServer.getHost();
    String serverUrl = "http://" + host + ":" + port + "/";
    String username = "root";
    String password = "root";
    influxDb = InfluxDBFactory.connect(serverUrl, username, password);
    influxDb.createDatabase(databaseName);
  }

  @AfterAll
  void cleanup() {
    influxDb.deleteDatabase(databaseName);
    influxDb.close();
    influxDbServer.stop();
  }

  @Test
  void testQueryAndModifyWithOneArgument() {
    String dbName = databaseName + "2";
    influxDb.createDatabase(dbName);
    BatchPoints batchPoints =
        BatchPoints.database(dbName).tag("async", "true").retentionPolicy("autogen").build();
    Point point1 =
        Point.measurement("cpu")
            .tag("atag", "test")
            .addField("idle", 90L)
            .addField("usertime", 9L)
            .addField("system", 1L)
            .build();
    Point point2 =
        Point.measurement("disk")
            .tag("atag", "test")
            .addField("used", 80L)
            .addField("free", 1L)
            .build();
    batchPoints.point(point1);
    batchPoints.point(point2);
    influxDb.write(batchPoints);
    Query query = new Query("SELECT * FROM cpu GROUP BY *", dbName);
    QueryResult result = influxDb.query(query);
    assertThat(result.getResults().get(0).getSeries().get(0).getTags()).isNotEmpty();
    influxDb.deleteDatabase(dbName);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("CREATE DATABASE " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfying(
                            attributeAssertions(null, "CREATE DATABASE", dbName))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("WRITE " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfying(attributeAssertions(null, "WRITE", dbName))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SELECT " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfying(
                            attributeAssertions("SELECT * FROM cpu GROUP BY *", "SELECT", dbName))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("DROP DATABASE " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfying(
                            attributeAssertions(null, "DROP DATABASE", dbName))));
  }

  @Test
  void testQueryWithTwoArguments() {
    Query query = new Query("SELECT * FROM cpu_load where test1 = 'influxDb'", databaseName);
    influxDb.query(query, TimeUnit.MILLISECONDS);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SELECT " + databaseName)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfying(
                            attributeAssertions(
                                "SELECT * FROM cpu_load where test1 = ?",
                                "SELECT",
                                databaseName))));
  }

  @Test
  void testQueryWithThreeArguments() throws InterruptedException {
    Query query =
        new Query(
            "SELECT * FROM cpu_load where time >= '2022-01-01T08:00:00Z' AND time <= '2022-01-01T20:00:00Z'",
            databaseName);
    BlockingQueue<QueryResult> queue = new LinkedBlockingQueue<>();

    influxDb.query(query, 2, result -> queue.add(result));
    queue.poll(20, TimeUnit.SECONDS);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SELECT " + databaseName)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfying(
                            attributeAssertions(
                                "SELECT * FROM cpu_load where time >= ? AND time <= ?",
                                "SELECT",
                                databaseName))));
  }

  @Test
  void testQueryWithThreeArgumentsCallback() throws InterruptedException {
    Query query = new Query("SELECT * FROM cpu_load", databaseName);
    BlockingQueue<QueryResult> queue = new LinkedBlockingQueue<>();

    influxDb.query(query, 2, result -> queue.add(result));
    queue.poll(20, TimeUnit.SECONDS);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SELECT " + databaseName)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfying(
                            attributeAssertions(
                                "SELECT * FROM cpu_load", "SELECT", databaseName))));
  }

  @Test
  void testQueryWithFiveArguments() throws InterruptedException {
    CountDownLatch countDownLatch = new CountDownLatch(1);
    Query query =
        new Query(
            "SELECT MEAN(water_level) FROM h2o_feet where time = '2022-01-01T08:00:00Z'; SELECT water_level FROM h2o_feet LIMIT 2",
            databaseName);
    testing.runWithSpan(
        "parent",
        () -> {
          influxDb.query(
              query,
              10,
              (cancellable, queryResult) -> countDownLatch.countDown(),
              () -> testing.runWithSpan("child", () -> {}),
              throwable -> {});
        });
    assertThat(countDownLatch.await(10, TimeUnit.SECONDS)).isTrue();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("SELECT " + databaseName)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfying(
                            attributeAssertions(
                                "SELECT MEAN(water_level) FROM h2o_feet where time = ?; SELECT water_level FROM h2o_feet LIMIT ?",
                                "SELECT",
                                databaseName)),
                span ->
                    span.hasName("child").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0))));
  }

  @Test
  void testQueryFailedWithFiveArguments() throws InterruptedException {
    CountDownLatch countDownLatchFailure = new CountDownLatch(1);
    Query query = new Query("SELECT MEAN(water_level) FROM;", databaseName);
    testing.runWithSpan(
        "parent",
        () -> {
          influxDb.query(
              query,
              10,
              (cancellable, queryResult) -> {},
              () -> {},
              throwable -> {
                testing.runWithSpan("child", () -> {});
                countDownLatchFailure.countDown();
              });
        });
    assertThat(countDownLatchFailure.await(10, TimeUnit.SECONDS)).isTrue();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("SELECT " + databaseName)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfying(
                            attributeAssertions(
                                "SELECT MEAN(water_level) FROM;", "SELECT", databaseName)),
                span ->
                    span.hasName("child").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0))));
  }

  @Test
  void testWriteWithFourArguments() {
    String measurement = "cpu_load";
    List<String> records = new ArrayList<>();
    records.add(measurement + ",atag=test1 idle=100,usertime=10,system=1 1485273600");
    influxDb.write(databaseName, "autogen", InfluxDB.ConsistencyLevel.ONE, records);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("WRITE " + databaseName)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfying(
                            attributeAssertions(null, "WRITE", databaseName))));
  }

  @Test
  void testWriteWithFiveArguments() {
    String measurement = "cpu_load";
    List<String> records = new ArrayList<>();
    records.add(measurement + ",atag=test1 idle=100,usertime=10,system=1 1485273600");
    influxDb.write(
        databaseName, "autogen", InfluxDB.ConsistencyLevel.ONE, TimeUnit.SECONDS, records);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("WRITE " + databaseName)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfying(
                            attributeAssertions(null, "WRITE", databaseName))));
  }

  @Test
  void testWriteWithUdp() {
    List<String> lineProtocols = new ArrayList<>();
    for (int i = 0; i < 2000; i++) {
      Point point = Point.measurement("udp_single_poit").addField("v", i).build();
      lineProtocols.add(point.lineProtocol());
    }
    influxDb.write(port, lineProtocols);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("WRITE")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfying(attributeAssertions(null, "WRITE", null))));
  }

  private static List<AttributeAssertion> attributeAssertions(
      String statement, String operation, String databaseName) {
    List<AttributeAssertion> result = new ArrayList<>();
    result.addAll(
        asList(
            equalTo(maybeStable(DB_SYSTEM), "influxdb"),
            equalTo(maybeStable(DB_NAME), databaseName),
            equalTo(SERVER_ADDRESS, host),
            equalTo(SERVER_PORT, port),
            equalTo(maybeStable(DB_OPERATION), operation)));
    if (statement != null) {
      result.add(equalTo(maybeStable(DB_STATEMENT), statement));
    }
    return result;
  }
}
