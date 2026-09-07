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
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Collection;
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
        SERVER_ADDRESS,
        SERVER_PORT);
  }

  @Test
  void testConfiguredNodesPreserveOrderAndRenderPortsAndIpv6() throws Exception {
    ClickHouseNode first =
        ClickHouseNode.builder(ClickHouseNode.of("http://first.example"))
            .host("2001:db8::1")
            .port(9123)
            .build();
    ClickHouseNode second = ClickHouseNode.of("http://second.example");
    ClickHouseNode third =
        ClickHouseNode.builder(ClickHouseNode.of("http://third.example")).port(8124).build();

    Object serverTarget = serverTarget(requestWithNodes(ImmutableList.of(first, second, third)));

    assertThat(serverTarget).isNotNull();
    assertThat(serverTargetAddress(serverTarget))
        .isEqualTo("[2001:db8::1]:9123,second.example:8123,third.example:8124");
    assertThat(serverTargetPort(serverTarget)).isNull();
  }

  @Test
  void testConfiguredDefaultPortIsReportedSeparately() throws Exception {
    Object serverTarget =
        serverTarget(
            requestWithNodes(ImmutableList.of(ClickHouseNode.of("http://default.example"))));

    assertThat(serverTarget).isNotNull();
    assertThat(serverTargetAddress(serverTarget)).isEqualTo("default.example");
    assertThat(serverTargetPort(serverTarget)).isNull();

    serverTarget =
        serverTarget(
            requestWithNodes(ImmutableList.of(ClickHouseNode.of("http://single.example:9123"))));

    assertThat(serverTarget).isNotNull();
    assertThat(serverTargetAddress(serverTarget)).isEqualTo("single.example");
    assertThat(serverTargetPort(serverTarget)).isEqualTo(9123);
  }

  @Test
  void testConfiguredNodesRejectUnsafeTarget() throws Exception {
    ClickHouseNode unsafe =
        ClickHouseNode.builder(ClickHouseNode.of("http://safe.example"))
            .host("user:password@unsafe.example")
            .build();

    assertThat(serverTarget(requestWithNodes(ImmutableList.of(unsafe)))).isNull();
  }

  @Test
  void testCopiedSealedRequestRetainsConfiguredTarget() throws Exception {
    String nodeList = "http://" + host + ":" + port + "," + host + ":" + (port + 1);
    String expectedAddress = host + ":" + port + "," + host + ":" + (port + 1);

    ClickHouseRequest<?> request =
        client
            .read(ClickHouseNodes.of(nodeList + "/" + DATABASE_NAME + "?compress=0"))
            .format(ClickHouseFormat.RowBinaryWithNamesAndTypes)
            .query("select * from " + TABLE_NAME)
            .seal()
            .copy();

    Object serverTarget = serverTarget(request);
    assertThat(serverTarget).isNotNull();
    assertThat(serverTargetAddress(serverTarget)).isEqualTo(expectedAddress);
    assertThat(serverTargetPort(serverTarget)).isNull();
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
                                maybeStable(DB_STATEMENT),
                                "select * from " + TABLE_NAME + " where s={s:String}"),
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

  private static Object serverTarget(ClickHouseRequest<?> request) throws Exception {
    return singletons(request)
        .getMethod("serverTarget", ClickHouseRequest.class)
        .invoke(null, request);
  }

  private static String serverTargetAddress(Object serverTarget) throws Exception {
    return (String) serverTarget.getClass().getMethod("getAddress").invoke(serverTarget);
  }

  private static Integer serverTargetPort(Object serverTarget) throws Exception {
    return (Integer) serverTarget.getClass().getMethod("getPort").invoke(serverTarget);
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
