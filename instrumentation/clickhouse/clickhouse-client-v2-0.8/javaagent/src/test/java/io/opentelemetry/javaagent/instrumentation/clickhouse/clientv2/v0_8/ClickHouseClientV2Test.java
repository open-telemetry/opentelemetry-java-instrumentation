/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv2.v0_8;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.CLICKHOUSE;
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
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

@SuppressWarnings("deprecation") // using deprecated semconv
class ClickHouseClientV2Test {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final GenericContainer<?> clickhouseServer =
      new GenericContainer<>("clickhouse/clickhouse-server:24.4.2").withExposedPorts(8123);

  private static final String DATABASE_NAME = "default";
  private static final String TABLE_NAME = "test_table";
  private static final String USERNAME = "default";
  private static final String PASSWORD = "";
  private static int port;
  private static String host;
  private static Client client;

  @BeforeAll
  static void setup() throws Exception {
    clickhouseServer.start();
    cleanup.deferAfterAll(clickhouseServer::stop);
    port = clickhouseServer.getMappedPort(8123);
    host = clickhouseServer.getHost();

    client =
        new Client.Builder()
            .addEndpoint(Protocol.HTTP, host, port, false)
            .setDefaultDatabase(DATABASE_NAME)
            .setUsername(USERNAME)
            .setPassword(PASSWORD)
            .setOption("compress", "false")
            .build();
    cleanup.deferAfterAll(client);

    QueryResponse response =
        client
            .query("create table if not exists " + TABLE_NAME + "(value String) engine=Memory")
            .join();
    response.close();

    // wait for CREATE operation and clear
    testing.waitForTraces(1);
    testing.clearData();
  }

  @Test
  void testConnectionStringWithoutDatabaseSpecifiedStillGeneratesSpans() throws Exception {
    Client client =
        new Client.Builder()
            .addEndpoint(Protocol.HTTP, host, port, false)
            .setOption("compress", "false")
            .setUsername(USERNAME)
            .setPassword(PASSWORD)
            .build();
    cleanup.deferCleanup(client);

    QueryResponse response = client.query("select * from " + TABLE_NAME).join();
    response.close();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "SELECT test_table"
                                : "SELECT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_STATEMENT), "select * from " + TABLE_NAME),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "SELECT test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"))));

    assertDurationMetric(
        testing,
        "io.opentelemetry.clickhouse-client-v2-0.8",
        DB_SYSTEM_NAME,
        DB_QUERY_SUMMARY,
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
              client.query("insert into " + TABLE_NAME + " values('1')('2')('3')").join();
          response.close();

          response = client.query("select * from " + TABLE_NAME).join();
          response.close();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "INSERT test_table"
                                : "INSERT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "insert into " + TABLE_NAME + " values(?)(?)(?)"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "INSERT test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "INSERT")),
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "SELECT test_table"
                                : "SELECT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_STATEMENT), "select * from " + TABLE_NAME),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "SELECT test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"))));
  }

  @Test
  void testQueryWithStringQueryAndId() throws Exception {
    testing.runWithSpan(
        "parent",
        () -> {
          QuerySettings querySettings = new QuerySettings();
          querySettings.setQueryId("test_query_id");

          QueryResponse response =
              client.query("select * from " + TABLE_NAME, querySettings).join();
          response.close();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "SELECT test_table"
                                : "SELECT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_STATEMENT), "select * from " + TABLE_NAME),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "SELECT test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"))));
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

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "SELECT non_existent_table"
                                : "SELECT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasStatus(StatusData.error())
                        .hasException(thrown)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_STATEMENT), "select * from non_existent_table"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "SELECT non_existent_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"),
                            equalTo(ERROR_TYPE, emitStableDatabaseSemconv() ? "60" : null))));
  }

  @Test
  void testSendQuery() throws Exception {
    testing.runWithSpan(
        "parent",
        () -> {
          try (CommandResponse results =
              client.execute("select * from " + TABLE_NAME + " limit 1").join()) {
            assertThat(results.getReadRows()).isEqualTo(0);
          }
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "SELECT test_table"
                                : "SELECT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "select * from " + TABLE_NAME + " limit ?"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "SELECT test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"))));
  }

  @Test
  void testSendQueryAll() {
    testing.runWithSpan(
        "parent",
        () -> {
          List<GenericRecord> records = client.queryAll("select * from " + TABLE_NAME + " limit 1");
          assertThat(records).isEmpty();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "SELECT test_table"
                                : "SELECT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "select * from " + TABLE_NAME + " limit ?"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "SELECT test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"))));
  }

  @Test
  void testSendQueryRecords() throws Exception {
    testing.runWithSpan(
        "parent",
        () -> {
          Records records =
              client.queryRecords("insert into " + TABLE_NAME + " values('test_value')").join();
          records.close();

          try (Records selectRecords =
              client.queryRecords("select * from " + TABLE_NAME + " limit 1").join()) {
            assertThat(selectRecords.getReadRows()).isEqualTo(1);
          }
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "INSERT test_table"
                                : "INSERT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "insert into " + TABLE_NAME + " values(?)"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "INSERT test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "INSERT")),
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "SELECT test_table"
                                : "SELECT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "select * from " + TABLE_NAME + " limit ?"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "SELECT test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"))));
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
                      "select * from " + TABLE_NAME + " where value={param_s: String}",
                      queryParams,
                      null)
                  .join();
          response.close();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "SELECT test_table"
                                : "SELECT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "select * from " + TABLE_NAME + " where value={param_s: String}"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "SELECT test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"))));
  }
}
