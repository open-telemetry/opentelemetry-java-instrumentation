/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.util.ArrayList;
import java.util.List;
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

@TestInstance(Lifecycle.PER_CLASS)
class InfluxDbClient24Test {

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

  private static List<AttributeAssertion> attributeAssertions(
      String statement, String operation, String databaseName) {
    List<AttributeAssertion> result = new ArrayList<>();
    result.addAll(
        asList(
            equalTo(DbIncubatingAttributes.DB_SYSTEM, "influxdb"),
            equalTo(DbIncubatingAttributes.DB_NAME, databaseName),
            equalTo(ServerAttributes.SERVER_ADDRESS, host),
            equalTo(ServerAttributes.SERVER_PORT, port),
            equalTo(DbIncubatingAttributes.DB_OPERATION, operation)));
    if (statement != null) {
      result.add(equalTo(DbIncubatingAttributes.DB_STATEMENT, statement));
    }
    return result;
  }
}
