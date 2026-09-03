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
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
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
import com.clickhouse.client.ClickHouseNodeSelector;
import com.clickhouse.client.ClickHouseNodes;
import com.clickhouse.client.ClickHouseParameterizedQuery;
import com.clickhouse.client.ClickHouseRequest;
import com.clickhouse.client.ClickHouseResponse;
import com.clickhouse.client.ClickHouseResponseSummary;
import com.clickhouse.data.ClickHouseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.testing.common.AgentClassLoaderAccess;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    // wait for CREATE operation
    testing.waitForTraces(1);
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
                                ? "select test_table"
                                : "SELECT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(
                                NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? host : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) port : null),
                            equalTo(maybeStable(DB_STATEMENT), "select * from " + TABLE_NAME),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "select test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"))));

    assertDurationMetric(
        testing,
        "io.opentelemetry.clickhouse-client-v1-0.5",
        DB_SYSTEM_NAME,
        DB_QUERY_SUMMARY,
        DB_NAMESPACE,
        NETWORK_PEER_ADDRESS,
        NETWORK_PEER_PORT,
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
                                ? "insert test_table"
                                : "INSERT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(
                                NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? host : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) port : null),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "insert into " + TABLE_NAME + " values(?)(?)(?)"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "insert test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "INSERT")),
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "select test_table"
                                : "SELECT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(
                                NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? host : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) port : null),
                            equalTo(maybeStable(DB_STATEMENT), "select * from " + TABLE_NAME),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "select test_table" : null),
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
                                ? "select test_table"
                                : "SELECT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(
                                NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? host : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) port : null),
                            equalTo(maybeStable(DB_STATEMENT), "select * from " + TABLE_NAME),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "select test_table" : null),
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
                                ? "select non_existent_table"
                                : "SELECT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasStatus(StatusData.error())
                        .hasException(thrown)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(
                                NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? host : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) port : null),
                            equalTo(maybeStable(DB_STATEMENT), "select * from non_existent_table"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "select non_existent_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"),
                            equalTo(ERROR_TYPE, emitStableDatabaseSemconv() ? "60" : null))));

    assertDurationMetric(
        testing,
        "io.opentelemetry.clickhouse-client-v1-0.5",
        DB_SYSTEM_NAME,
        DB_QUERY_SUMMARY,
        DB_NAMESPACE,
        ERROR_TYPE,
        NETWORK_PEER_ADDRESS,
        NETWORK_PEER_PORT,
        SERVER_ADDRESS,
        SERVER_PORT);
    if (emitStableDatabaseSemconv()) {
      testing.waitAndAssertMetrics(
          "io.opentelemetry.clickhouse-client-v1-0.5",
          metric ->
              metric
                  .hasName("db.client.operation.duration")
                  .hasHistogramSatisfying(
                      histogram ->
                          histogram.hasPointsSatisfying(
                              point -> point.hasAttribute(ERROR_TYPE, "60"))));
    }
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
                                ? "select test_table"
                                : "SELECT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(
                                NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? host : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) port : null),
                            equalTo(maybeStable(DB_STATEMENT), "select * from " + TABLE_NAME),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "select test_table" : null),
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
                                ? "select test_table"
                                : "SELECT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(
                                NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? host : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) port : null),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "select * from " + TABLE_NAME + " limit ?"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "select test_table" : null),
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
                                ? "insert test_table"
                                : "INSERT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(
                                NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? host : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) port : null),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "insert into " + TABLE_NAME + " values(?)(?)(?)"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "insert test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "INSERT")),
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "select test_table"
                                : "SELECT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(
                                NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? host : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) port : null),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "select * from " + TABLE_NAME + " limit ?"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "select test_table" : null),
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
                                ? "insert test_table"
                                : "INSERT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(
                                NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? host : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) port : null),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "insert into " + TABLE_NAME + " values(:val1)(:val2)(:val3)"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "insert test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "INSERT")),
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "select test_table"
                                : "SELECT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(
                                NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? host : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) port : null),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "select * from " + TABLE_NAME + " where s=:val"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "select test_table" : null),
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
                                ? "select test_table"
                                : "SELECT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(
                                NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? host : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) port : null),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "select * from " + TABLE_NAME + " where s={s:String}"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "select test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"))));
  }

  @Test
  void testNodeListReportsTheWholeConfiguredTarget() throws ClickHouseException {
    String nodeList = "http://" + host + ":" + port + "," + host + ":" + (port + 1);
    String addressGroup = host + ":" + port + "," + host + ":" + (port + 1);
    ClickHouseNodes nodes = ClickHouseNodes.of(nodeList + "/" + DATABASE_NAME + "?compress=0");

    ClickHouseResponse response =
        client
            .read(nodes)
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
                                ? "select test_table"
                                : "SELECT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(
                                SERVER_ADDRESS, emitStableDatabaseSemconv() ? addressGroup : host),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv() ? null : Long.valueOf(port)),
                            equalTo(
                                NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? host : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) port : null),
                            equalTo(maybeStable(DB_STATEMENT), "select * from " + TABLE_NAME),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "select test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"))));
  }

  @Test
  void testConfiguredNodeDefaultsOmitPortsAcrossProtocolAndSslVariants() throws Exception {
    ClickHouseRequest<?> request =
        requestWithNodes(
            ImmutableList.of(
                ClickHouseNode.of("tcps://tcps.example"),
                ClickHouseNode.of("tcp://tcp.example"),
                ClickHouseNode.of("https://https.example"),
                ClickHouseNode.of("http://http.example")));

    assertThat(serverAddressGroup(request))
        .isEqualTo("tcps.example,tcp.example,https.example,http.example");
    assertThat(serverPort(request)).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://http.example",
        "https://https.example",
        "tcp://tcp.example",
        "tcps://tcps.example"
      })
  void testDirectNodeDefaultPortsAreOmitted(String nodeUrl) throws Exception {
    ClickHouseRequest<?> request = client.read(ClickHouseNode.of(nodeUrl));

    assertThat(serverPort(request)).isNull();
  }

  @Test
  void testDirectNodeNonDefaultPortIsPreserved() throws Exception {
    ClickHouseNode node =
        ClickHouseNode.builder(ClickHouseNode.of("http://http.example")).port(12345).build();
    ClickHouseRequest<?> request = client.read(node);

    assertThat(serverPort(request)).isEqualTo(12345);
  }

  @Test
  void testConfiguredNodesInlineSharedNonDefaultPort() throws Exception {
    ClickHouseNode httpNode =
        ClickHouseNode.builder(ClickHouseNode.of("http://http.example")).port(12345).build();
    ClickHouseNode tcpNode =
        ClickHouseNode.builder(ClickHouseNode.of("tcp://tcp.example"))
            .host("2001:db8::2")
            .port(12345)
            .build();
    ClickHouseRequest<?> request = requestWithNodes(ImmutableList.of(httpNode, tcpNode));

    assertThat(serverAddressGroup(request)).isEqualTo("http.example:12345,[2001:db8::2]:12345");
    assertThat(serverPort(request)).isNull();
  }

  @Test
  void testConfiguredNodesInlineMixedPorts() throws Exception {
    ClickHouseNode httpNode = ClickHouseNode.of("http://http.example");
    ClickHouseNode httpsNode = ClickHouseNode.of("https://https.example:9444");
    ClickHouseRequest<?> request = requestWithNodes(ImmutableList.of(httpNode, httpsNode));

    assertThat(serverAddressGroup(request)).isEqualTo("http.example:8123,https.example:9444");
    assertThat(serverPort(request)).isNull();
  }

  @Test
  void testConfiguredNodesIncludeAtMostFiveEndpoints() throws Exception {
    ClickHouseNode first = ClickHouseNode.of("http://host1.example");
    ClickHouseNode second = ClickHouseNode.of("http://host2.example");
    ClickHouseNode third = ClickHouseNode.of("http://host3.example");
    ClickHouseNode fourth = ClickHouseNode.of("http://host4.example");
    ClickHouseNode fifth = ClickHouseNode.of("http://host5.example");
    ClickHouseNode sixth = ClickHouseNode.of("http://host6.example");
    String expected = "host1.example,host2.example,host3.example,host4.example,host5.example";

    assertThat(
            serverAddressGroup(
                requestWithNodes(ImmutableList.of(first, second, third, fourth, fifth))))
        .isEqualTo(expected);
    assertThat(
            serverAddressGroup(
                requestWithNodes(ImmutableList.of(first, second, third, fourth, fifth, sixth))))
        .isEqualTo(expected);
  }

  @Test
  void testConfiguredNodesUsePortModeFromAllEndpoints() throws Exception {
    ClickHouseNode nonDefaultSixth =
        ClickHouseNode.builder(ClickHouseNode.of("http://host6.example")).port(9123).build();
    ClickHouseRequest<?> request =
        requestWithNodes(
            ImmutableList.of(
                ClickHouseNode.of("http://host1.example"),
                ClickHouseNode.of("http://host2.example"),
                ClickHouseNode.of("http://host3.example"),
                ClickHouseNode.of("http://host4.example"),
                ClickHouseNode.of("http://host5.example"),
                nonDefaultSixth));

    assertThat(serverAddressGroup(request))
        .isEqualTo(
            "host1.example:8123,host2.example:8123,host3.example:8123,"
                + "host4.example:8123,host5.example:8123");
    assertThat(serverPort(request)).isNull();
  }

  @Test
  void testConfiguredNodesValidateEndpointsAfterTheLimit() throws Exception {
    ClickHouseNode invalidSixth =
        ClickHouseNode.builder(ClickHouseNode.of("http://host6.example"))
            .host("invalid.example/path")
            .build();
    ClickHouseRequest<?> request =
        requestWithNodes(
            ImmutableList.of(
                ClickHouseNode.of("http://host1.example"),
                ClickHouseNode.of("http://host2.example"),
                ClickHouseNode.of("http://host3.example"),
                ClickHouseNode.of("http://host4.example"),
                ClickHouseNode.of("http://host5.example"),
                invalidSixth));

    assertThat(serverAddressGroup(request)).isNull();
    assertThat(serverPort(request)).isNull();
  }

  @Test
  void testConfiguredNodeOrderPreservesPriorityAndDuplicates() throws Exception {
    ClickHouseNode ipv6Node = ClickHouseNode.builder(server).host("2001:db8::2").build();
    ClickHouseNodes nodes = createNodes(ImmutableList.of(ipv6Node, server, ipv6Node));
    nodes.update(ipv6Node, ClickHouseNode.Status.FAULTY);
    String ipv6Address = "[2001:db8::2]:" + port;
    String addressGroup = ipv6Address + "," + host + ":" + port + "," + ipv6Address;

    ClickHouseResponse response =
        client
            .read(nodes)
            .format(ClickHouseFormat.RowBinaryWithNamesAndTypes)
            .query("select * from " + TABLE_NAME)
            .executeAndWait();
    response.close();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(
                                SERVER_ADDRESS, emitStableDatabaseSemconv() ? addressGroup : host),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv() ? null : Long.valueOf(port)),
                            equalTo(
                                NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? host : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) port : null),
                            equalTo(maybeStable(DB_STATEMENT), "select * from " + TABLE_NAME),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "select test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"))));
  }

  @Test
  void testSingleNodeListRetainsConfiguredTarget() throws ClickHouseException {
    ClickHouseNodes nodes =
        ClickHouseNodes.of("single-node", "http://" + host + ":" + port, ImmutableMap.of());
    ClickHouseRequest<?> request =
        client
            .read(nodes)
            .format(ClickHouseFormat.RowBinaryWithNamesAndTypes)
            .query("select * from " + TABLE_NAME);

    ClickHouseResponse response = client.executeAndWait(request);
    response.close();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(
                                NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? host : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) port : null),
                            equalTo(maybeStable(DB_STATEMENT), "select * from " + TABLE_NAME),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "select test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"))));
  }

  @Test
  void testSelectorOutputIsNotAConfiguredTarget() throws ClickHouseException {
    ClickHouseRequest<?> request =
        client
            .read(
                (Function<ClickHouseNodeSelector, ClickHouseNode> & Serializable)
                    selector -> server,
                ImmutableMap.of())
            .format(ClickHouseFormat.RowBinaryWithNamesAndTypes)
            .query("select * from " + TABLE_NAME);

    ClickHouseResponse response = client.executeAndWait(request);
    response.close();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, emitStableDatabaseSemconv() ? null : host),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv() ? null : Long.valueOf(port)),
                            equalTo(
                                NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? host : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) port : null),
                            equalTo(maybeStable(DB_STATEMENT), "select * from " + TABLE_NAME),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "select test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"))));
  }

  @Test
  void testConfiguredNodeHostsRemoveCredentials() throws Exception {
    ClickHouseNode credentialNode =
        ClickHouseNode.builder(server).host("user:secret@configured.example").build();
    ClickHouseNodes nodes = createNodes(ImmutableList.of(server, credentialNode));
    nodes.update(credentialNode, ClickHouseNode.Status.FAULTY);
    String addressGroup = host + ":" + port + ",configured.example:" + port;

    ClickHouseResponse response =
        client
            .read(nodes)
            .format(ClickHouseFormat.RowBinaryWithNamesAndTypes)
            .query("select * from " + TABLE_NAME)
            .executeAndWait();
    response.close();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(
                                SERVER_ADDRESS, emitStableDatabaseSemconv() ? addressGroup : host),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv() ? null : Long.valueOf(port)),
                            equalTo(
                                NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? host : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) port : null),
                            equalTo(maybeStable(DB_STATEMENT), "select * from " + TABLE_NAME),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "select test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"))));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "host1,host2",
        "host?email=user@example.com",
        "configured.example%3Fpassword%3Dsecret",
        "[configured.example]"
      })
  void testConfiguredNodeHostsRejectMalformedValues(String configuredHost) throws Exception {
    ClickHouseNode malformedNode = ClickHouseNode.builder(server).host(configuredHost).build();
    ClickHouseNodes nodes = createNodes(ImmutableList.of(server, malformedNode));
    nodes.update(malformedNode, ClickHouseNode.Status.FAULTY);

    ClickHouseResponse response =
        client
            .read(nodes)
            .format(ClickHouseFormat.RowBinaryWithNamesAndTypes)
            .query("select * from " + TABLE_NAME)
            .executeAndWait();
    response.close();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, emitStableDatabaseSemconv() ? null : host),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv() ? null : Long.valueOf(port)),
                            equalTo(
                                NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? host : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) port : null),
                            equalTo(maybeStable(DB_STATEMENT), "select * from " + TABLE_NAME),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "select test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"))));
  }

  @Test
  void testCopiedSealedNodeListReportsTheWholeConfiguredTarget() throws ClickHouseException {
    String nodeList = "http://" + host + ":" + port + "," + host + ":" + (port + 1);
    String addressGroup = host + ":" + port + "," + host + ":" + (port + 1);
    ClickHouseNodes nodes = ClickHouseNodes.of(nodeList + "/" + DATABASE_NAME + "?compress=0");
    ClickHouseRequest<?> request =
        client
            .read(nodes)
            .format(ClickHouseFormat.RowBinaryWithNamesAndTypes)
            .query("select * from " + TABLE_NAME)
            .seal()
            .copy();

    ClickHouseResponse response = client.executeAndWait(request);
    response.close();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(
                                SERVER_ADDRESS, emitStableDatabaseSemconv() ? addressGroup : host),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv() ? null : Long.valueOf(port)),
                            equalTo(
                                NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? host : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) port : null),
                            equalTo(maybeStable(DB_STATEMENT), "select * from " + TABLE_NAME),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "select test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"))));
  }

  @Test
  void testCopiedSealedMutationPreservesConfiguredTarget() throws ClickHouseException {
    String nodeList = "http://" + host + ":" + port + "," + host + ":" + (port + 1);
    String addressGroup = host + ":" + port + "," + host + ":" + (port + 1);
    ClickHouseNodes nodes = ClickHouseNodes.of(nodeList + "/" + DATABASE_NAME + "?compress=0");

    ClickHouseRequest<?> request =
        client
            .read(nodes)
            .seal()
            .copy()
            .write()
            .query("insert into " + TABLE_NAME + " values('4')")
            .seal();

    ClickHouseResponse response = client.executeAndWait(request);
    response.close();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(
                                SERVER_ADDRESS, emitStableDatabaseSemconv() ? addressGroup : host),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv() ? null : Long.valueOf(port)),
                            equalTo(
                                NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? host : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) port : null),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "insert into " + TABLE_NAME + " values(?)"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "insert test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "INSERT"))));
  }

  @Test
  void testFaultyNodeStaysInTheConfiguredTarget() throws ClickHouseException {
    String nodeList = "http://" + host + ":" + port + "," + host + ":" + (port + 2);
    String addressGroup = host + ":" + port + "," + host + ":" + (port + 2);
    ClickHouseNodes nodes = ClickHouseNodes.of(nodeList + "/" + DATABASE_NAME + "?compress=0");

    for (ClickHouseNode node : nodes.getNodes()) {
      if (node.getPort() == port + 2) {
        nodes.update(node, ClickHouseNode.Status.FAULTY);
      }
    }
    assertThat(nodes.getNodes()).hasSize(1);

    ClickHouseResponse response =
        client
            .read(nodes)
            .format(ClickHouseFormat.RowBinaryWithNamesAndTypes)
            .query("select * from " + TABLE_NAME)
            .executeAndWait();
    response.close();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(
                                SERVER_ADDRESS, emitStableDatabaseSemconv() ? addressGroup : host),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv() ? null : Long.valueOf(port)),
                            equalTo(
                                NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? host : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) port : null),
                            equalTo(maybeStable(DB_STATEMENT), "select * from " + TABLE_NAME),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "select test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"))));
  }

  private static ClickHouseNodes createNodes(Collection<ClickHouseNode> nodes) throws Exception {
    Constructor<ClickHouseNodes> constructor =
        ClickHouseNodes.class.getDeclaredConstructor(Collection.class);
    constructor.setAccessible(true);
    return constructor.newInstance(nodes);
  }

  private static ClickHouseRequest<?> requestWithNodes(Collection<ClickHouseNode> nodes)
      throws Exception {
    return client.read(createNodes(nodes));
  }

  private static String serverAddressGroup(ClickHouseRequest<?> request) throws Exception {
    return (String)
        singletons(request)
            .getMethod("serverAddressGroup", ClickHouseRequest.class)
            .invoke(null, request);
  }

  private static Integer serverPort(ClickHouseRequest<?> request) throws Exception {
    return (Integer)
        singletons(request).getMethod("serverPort", ClickHouseRequest.class).invoke(null, request);
  }

  private static Class<?> singletons(ClickHouseRequest<?> request) throws Exception {
    String singletonsName =
        "io.opentelemetry.javaagent.instrumentation.clickhouse.clientv1.v0_5."
            + "ClickHouseClientV1Singletons";
    ClassLoader requestClassLoader = request.getClass().getClassLoader();
    try {
      return Class.forName(singletonsName, true, requestClassLoader);
    } catch (ClassNotFoundException ignored) {
      Class<?> registry =
          AgentClassLoaderAccess.loadClass(
              "io.opentelemetry.javaagent.tooling.instrumentation.indy.IndyModuleRegistry");
      Method getInstrumentationClassLoader =
          registry.getMethod("getInstrumentationClassLoader", String.class, ClassLoader.class);
      ClassLoader instrumentationClassLoader =
          (ClassLoader)
              getInstrumentationClassLoader.invoke(
                  null,
                  "io.opentelemetry.javaagent.instrumentation.clickhouse.clientv1.v0_5."
                      + "ClickHouseClientV1InstrumentationModule",
                  requestClassLoader);
      return Class.forName(singletonsName, true, instrumentationClassLoader);
    }
  }
}
