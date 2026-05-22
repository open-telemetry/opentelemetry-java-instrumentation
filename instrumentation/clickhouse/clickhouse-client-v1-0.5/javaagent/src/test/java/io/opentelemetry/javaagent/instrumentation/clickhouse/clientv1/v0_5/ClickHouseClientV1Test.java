/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv1.v0_5;

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

import com.clickhouse.client.ClickHouseClient;
import com.clickhouse.client.ClickHouseException;
import com.clickhouse.client.ClickHouseNode;
import com.clickhouse.client.ClickHouseParameterizedQuery;
import com.clickhouse.client.ClickHouseRequest;
import com.clickhouse.client.ClickHouseResponse;
import com.clickhouse.client.ClickHouseResponseSummary;
import com.clickhouse.data.ClickHouseFormat;
import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

@SuppressWarnings("deprecation") // using deprecated semconv
class ClickHouseClientV1Test {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final GenericContainer<?> clickhouseServer =
      new GenericContainer<>("clickhouse/clickhouse-server:24.4.2").withExposedPorts(8123);

  private static final String DATABASE_NAME = "default";
  private static final String TABLE_NAME = "test_table";
  private static int port;
  private static String host;
  private static ClickHouseNode server;
  private static ClickHouseClient client;

  @BeforeAll
  static void setup() throws ClickHouseException {
    clickhouseServer.start();
    cleanup.deferAfterAll(clickhouseServer::stop);
    port = clickhouseServer.getMappedPort(8123);
    host = clickhouseServer.getHost();
    server = ClickHouseNode.of("http://" + host + ":" + port + "/" + DATABASE_NAME + "?compress=0");
    client = ClickHouseClient.builder().build();
    cleanup.deferAfterAll(client);

    ClickHouseResponse response =
        client
            .read(server)
            .query("create table if not exists " + TABLE_NAME + "(s String) engine=Memory")
            .executeAndWait();
    response.close();

    // wait for CREATE operation and clear
    testing.waitForTraces(1);
    testing.clearData();
  }

  @Test
  void testConnectionStringWithoutDatabaseSpecifiedStillGeneratesSpans()
      throws ClickHouseException {
    ClickHouseNode server = ClickHouseNode.of("http://" + host + ":" + port + "?compress=0");
    ClickHouseClient client = ClickHouseClient.builder().build();
    cleanup.deferCleanup(client);

    ClickHouseResponse response =
        client
            .read(server)
            .format(ClickHouseFormat.RowBinaryWithNamesAndTypes)
            .query("select * from " + TABLE_NAME)
            .executeAndWait();
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
        "io.opentelemetry.clickhouse-client-v1-0.5",
        DB_SYSTEM_NAME,
        DB_QUERY_SUMMARY,
        DB_NAMESPACE,
        SERVER_ADDRESS,
        SERVER_PORT);
  }

  @Test
  void testExecuteAndWaitWithStringQuery() throws ClickHouseException {
    testing.runWithSpan(
        "parent",
        () -> {
          ClickHouseResponse response;
          response =
              client
                  .write(server)
                  .query("insert into " + TABLE_NAME + " values('1')('2')('3')")
                  .executeAndWait();
          response.close();

          response =
              client
                  .read(server)
                  .format(ClickHouseFormat.RowBinaryWithNamesAndTypes)
                  .query("select * from " + TABLE_NAME)
                  .executeAndWait();
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
  void testExecuteAndWaitWithStringQueryAndId() throws ClickHouseException {
    testing.runWithSpan(
        "parent",
        () -> {
          ClickHouseResponse response =
              client
                  .read(server)
                  .format(ClickHouseFormat.RowBinaryWithNamesAndTypes)
                  .query("select * from " + TABLE_NAME, "test_query_id")
                  .executeAndWait();
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
  void testExecuteAndWaitThrowsException() {
    Throwable thrown =
        catchThrowable(
            () -> {
              ClickHouseResponse response =
                  client
                      .read(server)
                      .format(ClickHouseFormat.RowBinaryWithNamesAndTypes)
                      .query("select * from non_existent_table")
                      .executeAndWait();
              response.close();
            });

    assertThat(thrown).isInstanceOf(ClickHouseException.class);

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
  void testAsyncExecuteQuery() {
    CompletableFuture<ClickHouseResponse> response =
        client
            .read(server)
            .format(ClickHouseFormat.RowBinaryWithNamesAndTypes)
            .query("select * from " + TABLE_NAME)
            .execute();

    ClickHouseResponse result = response.join();
    assertThat(result).isNotNull();
    result.close();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "SELECT test_table"
                                : "SELECT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
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
  void testSendQuery() {
    testing.runWithSpan(
        "parent",
        () -> {
          CompletableFuture<List<ClickHouseResponseSummary>> future =
              ClickHouseClient.send(server, "select * from " + TABLE_NAME + " limit 1");
          List<ClickHouseResponseSummary> results = future.join();
          assertThat(results).hasSize(1);
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
  void testSendMultipleQueries() {
    testing.runWithSpan(
        "parent",
        () -> {
          CompletableFuture<List<ClickHouseResponseSummary>> future =
              ClickHouseClient.send(
                  server,
                  "insert into " + TABLE_NAME + " values('1')('2')('3')",
                  "select * from " + TABLE_NAME + " limit 1");
          List<ClickHouseResponseSummary> results = future.join();
          assertThat(results).hasSize(2);
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
  void testParameterizedQueryInput() throws ClickHouseException {
    ClickHouseRequest<?> request =
        client.read(server).format(ClickHouseFormat.RowBinaryWithNamesAndTypes);

    testing.runWithSpan(
        "parent",
        () -> {
          ClickHouseResponse response =
              client
                  .write(server)
                  .query(
                      ClickHouseParameterizedQuery.of(
                          request.getConfig(),
                          "insert into " + TABLE_NAME + " values(:val1)(:val2)(:val3)"))
                  .params(ImmutableMap.of("val1", "1", "val2", "2", "val3", "3"))
                  .executeAndWait();
          response.close();

          response =
              request
                  .query(
                      ClickHouseParameterizedQuery.of(
                          request.getConfig(), "select * from " + TABLE_NAME + " where s=:val"))
                  .params(ImmutableMap.of("val", "'2'"))
                  .executeAndWait();
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
                                "insert into " + TABLE_NAME + " values(:val1)(:val2)(:val3)"),
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
                                "select * from " + TABLE_NAME + " where s=:val"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "SELECT test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"))));
  }

  // regression test for
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/13019
  // {s:String} used in the query really a syntax error, should be {s: String}. This test verifies
  // that this syntax error isn't detected when running with the agent as it is also ignored when
  // running without the agent.
  @Test
  void testPlaceholderQueryInput() {
    ClickHouseRequest<?> request =
        client.read(server).format(ClickHouseFormat.RowBinaryWithNamesAndTypes);
    testing.runWithSpan(
        "parent",
        () -> {
          ClickHouseResponse response =
              request
                  // {s:String} is really a syntax error should be {s: String}
                  .query("select * from " + TABLE_NAME + " where s={s:String}")
                  .settings(ImmutableMap.of("param_s", "" + Instant.now().getEpochSecond()))
                  .execute()
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
                                "select * from " + TABLE_NAME + " where s={s:String}"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "SELECT test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"))));
  }
}
