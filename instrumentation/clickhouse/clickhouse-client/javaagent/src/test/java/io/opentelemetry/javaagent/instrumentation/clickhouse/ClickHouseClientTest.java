/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Arrays.asList;

import com.clickhouse.client.ClickHouseClient;
import com.clickhouse.client.ClickHouseConfig;
import com.clickhouse.client.ClickHouseException;
import com.clickhouse.client.ClickHouseNode;
import com.clickhouse.client.ClickHouseParameterizedQuery;
import com.clickhouse.client.ClickHouseRequest;
import com.clickhouse.client.ClickHouseResponse;
import com.clickhouse.client.config.ClickHouseClientOption;
import com.clickhouse.config.ClickHouseOption;
import com.clickhouse.data.ClickHouseFormat;
import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.io.Serializable;
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

  private static final String dbName = "default";
  private static final String tableName = "test_table";
  private static int port;
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

    ClickHouseResponse response =
        client
            .read(server)
            .query("create table if not exists " + tableName + "(s String) engine=Memory")
            .executeAndWait();
    response.close();

    // wait for CREATE operation and clear
    testing.waitForTraces(1);
    testing.clearData();
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
    ClickHouseResponse response;

    response =
        client
            .read(server)
            .query("insert into " + tableName + " values('1')('2')('3')")
            .executeAndWait();
    response.close();

    response =
        client
            .read(server)
            .format(ClickHouseFormat.RowBinaryWithNamesAndTypes)
            .query("select * from " + tableName)
            .executeAndWait();
    response.close();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("INSERT " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            attributeAssertions(
                                "insert into " + tableName + " values(?)(?)(?)", "INSERT"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SELECT " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            attributeAssertions("select * from " + tableName, "SELECT"))));
  }

  @Test
  void testQueryWithId() throws ClickHouseException {
    ClickHouseResponse response =
        client
            .read(server)
            .format(ClickHouseFormat.RowBinaryWithNamesAndTypes)
            .query("select * from " + tableName, "test_query_id")
            .executeAndWait();
    response.close();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SELECT " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            attributeAssertions("select * from " + tableName, "SELECT"))));
  }

  @Test
  void testClickHouseParameterizedQueryInput() throws ClickHouseException {
    ClickHouseRequest<?> request =
        client.read(server).format(ClickHouseFormat.RowBinaryWithNamesAndTypes);

    ClickHouseResponse response =
        client
            .read(server)
            .query(
                ClickHouseParameterizedQuery.of(
                    request.getConfig(),
                    "insert into " + tableName + " values(:val1)(:val2)(:val3)"))
            .params(ImmutableMap.of("val1", "1", "val2", "2", "val3", "3"))
            .executeAndWait();
    response.close();

    response =
        request
            .query(
                ClickHouseParameterizedQuery.of(
                    request.getConfig(), "select * from " + tableName + " where s=:val"))
            .params(ImmutableMap.of("val", "'2'"))
            .executeAndWait();
    response.close();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("INSERT " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            attributeAssertions(
                                "insert into " + tableName + " values(:val1)(:val2)(:val3)",
                                "INSERT"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SELECT " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            attributeAssertions(
                                "select * from " + tableName + " where s=:val", "SELECT"))));
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
}
