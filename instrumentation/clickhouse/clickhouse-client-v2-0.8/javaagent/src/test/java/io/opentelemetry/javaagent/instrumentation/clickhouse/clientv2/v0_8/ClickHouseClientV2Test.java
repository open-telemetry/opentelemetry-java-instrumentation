/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv2.v0_8;

import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.ServerException;
import com.clickhouse.client.api.command.CommandResponse;
import com.clickhouse.client.api.enums.Protocol;
import com.clickhouse.client.api.query.GenericRecord;
import com.clickhouse.client.api.query.QueryResponse;
import com.clickhouse.client.api.query.QuerySettings;
import com.clickhouse.client.api.query.Records;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

class ClickHouseClientV2Test {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final GenericContainer<?> clickhouseServer =
      new GenericContainer<>("clickhouse/clickhouse-server:24.4.2").withExposedPorts(8123);

  private static final String dbName = "default";
  private static final String tableName = "test_table";
  private static int port;
  private static String host;
  private static Client client;
  private static final String username = "default";
  private static final String password = "";

  @BeforeAll
  static void setup() throws Exception {
    clickhouseServer.start();
    port = clickhouseServer.getMappedPort(8123);
    host = clickhouseServer.getHost();

    client =
        new Client.Builder()
            .addEndpoint(Protocol.HTTP, host, port, false)
            .setDefaultDatabase(dbName)
            .setUsername(username)
            .setPassword(password)
            .setOption("compress", "false")
            .build();

    QueryResponse response =
        client
            .query("create table if not exists " + tableName + "(value String) engine=Memory")
            .get();
    response.close();

    // wait for CREATE operation and clear
    testing.waitForTraces(1);
    testing.clearData();
  }

  @AfterAll
  static void cleanup() {
    if (client != null) {
      client.close();
    }
    clickhouseServer.stop();
  }

  @Test
  void testConnectionStringWithoutDatabaseSpecifiedStillGeneratesSpans() throws Exception {
    Client client =
        new Client.Builder()
            .addEndpoint(Protocol.HTTP, host, port, false)
            .setOption("compress", "false")
            .setUsername(username)
            .setPassword(password)
            .build();

    QueryResponse response = client.query("select * from " + tableName).get();
    response.close();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SELECT " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            attributeAssertions("select * from " + tableName, "SELECT"))));

    assertDurationMetric(
        testing,
        "io.opentelemetry.clickhouse-clientv2-0.8",
        DB_SYSTEM_NAME,
        DB_OPERATION_NAME,
        DB_NAMESPACE,
        SERVER_ADDRESS,
        SERVER_PORT);
  }

  @Test
  void testQueryWithStringQuery() throws Exception {
    testing.runWithSpan(
        "parent",
        () -> {
          QueryResponse response =
              client.query("insert into " + tableName + " values('1')('2')('3')").get();
          response.close();

          response = client.query("select * from " + tableName).get();
          response.close();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("INSERT " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            attributeAssertions(
                                "insert into " + tableName + " values(?)(?)(?)", "INSERT")),
                span ->
                    span.hasName("SELECT " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            attributeAssertions("select * from " + tableName, "SELECT"))));
  }

  @Test
  void testQueryWithStringQueryAndId() throws Exception {
    testing.runWithSpan(
        "parent",
        () -> {
          QuerySettings querySettings = new QuerySettings();
          querySettings.setQueryId("test_query_id");

          QueryResponse response = client.query("select * from " + tableName, querySettings).get();
          response.close();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("SELECT " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            attributeAssertions("select * from " + tableName, "SELECT"))));
  }

  @Test
  void testQueryThrowsServerException() {
    Throwable thrown =
        catchThrowable(
            () -> {
              QueryResponse response = client.query("select * from non_existent_table").get();
              response.close();
            });

    assertThat(thrown).isInstanceOf(ServerException.class);

    List<AttributeAssertion> assertions =
        new ArrayList<>(attributeAssertions("select * from non_existent_table", "SELECT"));
    if (SemconvStability.emitStableDatabaseSemconv()) {
      assertions.add(equalTo(DB_RESPONSE_STATUS_CODE, "60"));
      assertions.add(equalTo(ERROR_TYPE, "com.clickhouse.client.api.ServerException"));
    }
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SELECT " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasStatus(StatusData.error())
                        .hasException(thrown)
                        .hasAttributesSatisfyingExactly(assertions)));
  }

  @Test
  void testSendQuery() throws Exception {
    testing.runWithSpan(
        "parent",
        () -> {
          CompletableFuture<CommandResponse> future =
              client.execute("select * from " + tableName + " limit 1");
          CommandResponse results = future.get();
          assertThat(results.getReadRows()).isEqualTo(0);
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("SELECT " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            attributeAssertions(
                                "select * from " + tableName + " limit ?", "SELECT"))));
  }

  @Test
  void testSendQueryAll() {
    testing.runWithSpan(
        "parent",
        () -> {
          List<GenericRecord> records = client.queryAll("select * from " + tableName + " limit 1");
          assertThat(records.size()).isEqualTo(0);
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("SELECT " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            attributeAssertions(
                                "select * from " + tableName + " limit ?", "SELECT"))));
  }

  @Test
  void testSendQueryRecords() throws Exception {
    testing.runWithSpan(
        "parent",
        () -> {
          Records records =
              client.queryRecords("insert into " + tableName + " values('test_value')").get();
          records.close();

          records = client.queryRecords("select * from " + tableName + " limit 1").get();
          records.close();
          assertThat(records.getReadRows()).isEqualTo(1);
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("INSERT " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            attributeAssertions(
                                "insert into " + tableName + " values(?)", "INSERT")),
                span ->
                    span.hasName("SELECT " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            attributeAssertions(
                                "select * from " + tableName + " limit ?", "SELECT"))));
  }

  @Test
  void testPlaceholderQuery() throws Exception {
    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put("param_s", Instant.now().getEpochSecond());

    testing.runWithSpan(
        "parent",
        () -> {
          QueryResponse response =
              client
                  .query(
                      "select * from " + tableName + " where value={param_s: String}",
                      queryParams,
                      null)
                  .get();
          response.close();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("SELECT " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            attributeAssertions(
                                "select * from " + tableName + " where value={param_s: String}",
                                "SELECT"))));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  private static List<AttributeAssertion> attributeAssertions(String statement, String operation) {
    return asList(
        equalTo(maybeStable(DB_SYSTEM), DbIncubatingAttributes.DbSystemIncubatingValues.CLICKHOUSE),
        equalTo(maybeStable(DB_NAME), dbName),
        equalTo(SERVER_ADDRESS, host),
        equalTo(SERVER_PORT, port),
        equalTo(maybeStable(DB_STATEMENT), statement),
        equalTo(maybeStable(DB_OPERATION), operation));
  }
}
