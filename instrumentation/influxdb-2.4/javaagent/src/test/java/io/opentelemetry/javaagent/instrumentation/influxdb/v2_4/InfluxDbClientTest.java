/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static io.opentelemetry.instrumentation.api.semconv.network.internal.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.instrumentation.api.semconv.network.internal.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.SemanticAttributes.DB_CONNECTION_STRING;
import static io.opentelemetry.semconv.SemanticAttributes.DB_NAME;
import static io.opentelemetry.semconv.SemanticAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.SemanticAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.SemanticAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.SemanticAttributes.NETWORK_TYPE;
import static java.util.Arrays.asList;

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
import org.junit.Assert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

// ignore @Deprecated annotation warning.
@SuppressWarnings("deprecation")
@TestInstance(Lifecycle.PER_CLASS)
class InfluxDbClientTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final GenericContainer<?> influxDbServer =
      new GenericContainer<>("influxdb:1.8.10-alpine").withExposedPorts(8086);

  private static InfluxDB influxDb;

  private static final String databaseName = "mydb";

  private static String serverURL;

  private static int port;

  @BeforeAll
  void setup() {
    influxDbServer.start();
    port = influxDbServer.getMappedPort(8086);
    String host = influxDbServer.getHost();
    serverURL = "http://" + host + ":" + port + "/";
    String username = "root";
    String password = "root";
    influxDb = InfluxDBFactory.connect(serverURL, username, password);
    influxDb.createDatabase(databaseName);
  }

  @AfterAll
  void cleanup() {
    influxDb.deleteDatabase(databaseName);
    influxDb.close();
  }

  @BeforeEach
  void reset() {
    testing.clearData();
  }

  @Test
  void testQueryAndModifyWithOneArgument() {
    String dbName = databaseName + System.currentTimeMillis();
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
    Assert.assertFalse(result.getResults().get(0).getSeries().get(0).getTags().isEmpty());
    influxDb.deleteDatabase(dbName);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("CREATE " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfying(
                            attributeAssertions(
                                String.format("CREATE DATABASE \"%s\"", dbName),
                                "CREATE",
                                dbName))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("write " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfying(attributeAssertions("write", "write", dbName))),
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
                    span.hasName("DROP " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfying(
                            attributeAssertions(
                                String.format("DROP DATABASE \"%s\"", dbName), "DROP", dbName))));
  }

  @Test
  void testQueryWithTwoArguments() {
    Query query = new Query("SELECT * FROM cpu_load", databaseName);
    influxDb.query(query, TimeUnit.MILLISECONDS);

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
  void testQueryWithThreeArguments() throws InterruptedException {
    Query query = new Query("SELECT * FROM cpu_load", databaseName);
    BlockingQueue<QueryResult> queue = new LinkedBlockingQueue<>();

    influxDb.query(query, 2, result -> queue.add(result));
    Thread.sleep(2000);
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
  void testQueryWithThreeArgumentsCallback() throws InterruptedException {
    Query query = new Query("SELECT * FROM cpu_load", databaseName);
    BlockingQueue<QueryResult> queue = new LinkedBlockingQueue<>();

    influxDb.query(query, 2, result -> queue.add(result));
    Thread.sleep(2000);
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
    CountDownLatch countDownLatchFailure = new CountDownLatch(1);
    Query query = new Query("SELECT * FROM cpu_load", databaseName);
    influxDb.query(
        query,
        10,
        (cancellable, queryResult) -> {
          countDownLatch.countDown();
        },
        () -> {},
        throwable -> {
          countDownLatchFailure.countDown();
        });
    Assertions.assertFalse(countDownLatchFailure.await(1, TimeUnit.SECONDS));
    Assertions.assertTrue(countDownLatch.await(1, TimeUnit.SECONDS));

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
  void testWriteWithFourArguments() {
    String measurement = "cpu_load";
    List<String> records = new ArrayList<>();
    records.add(measurement + ",atag=test1 idle=100,usertime=10,system=1 1485273600");
    influxDb.write(databaseName, "autogen", InfluxDB.ConsistencyLevel.ONE, records);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("write " + databaseName)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfying(
                            attributeAssertions("write", "write", databaseName))));
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
                    span.hasName("write " + databaseName)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfying(
                            attributeAssertions("write", "write", databaseName))));
  }

  @Test
  void testWriteWithUdp() {
    List<String> lineProtocols = new ArrayList<String>();
    for (int i = 0; i < 2000; i++) {
      Point point = Point.measurement("udp_single_poit").addField("v", i).build();
      lineProtocols.add(point.lineProtocol());
    }
    influxDb.write(port, lineProtocols);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("write unknown")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfying(
                            attributeAssertions("write", "write", "unknown"))));
  }

  private static List<AttributeAssertion> attributeAssertions(
      String statement, String operation, String databaseName) {
    return asList(
        equalTo(NETWORK_TYPE, "ipv4"),
        equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
        equalTo(NETWORK_PEER_PORT, port),
        equalTo(DB_SYSTEM, "influxdb"),
        equalTo(DB_CONNECTION_STRING, serverURL),
        equalTo(DB_NAME, databaseName),
        equalTo(DB_STATEMENT, statement),
        equalTo(DB_OPERATION, operation));
  }
}
