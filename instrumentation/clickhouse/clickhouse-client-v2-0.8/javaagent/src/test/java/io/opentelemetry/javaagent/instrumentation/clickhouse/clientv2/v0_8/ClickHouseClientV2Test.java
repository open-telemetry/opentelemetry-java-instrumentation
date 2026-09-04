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
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.CLICKHOUSE;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.ServerException;
import com.clickhouse.client.api.command.CommandResponse;
import com.clickhouse.client.api.enums.Protocol;
import com.clickhouse.client.api.query.GenericRecord;
import com.clickhouse.client.api.query.QueryResponse;
import com.clickhouse.client.api.query.QuerySettings;
import com.clickhouse.client.api.query.Records;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.UrlParser;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.testing.common.AgentClassLoaderAccess;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    // wait for CREATE operation
    testing.waitForTraces(1);
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
        "io.opentelemetry.clickhouse-client-v2-0.8",
        DB_SYSTEM_NAME,
        DB_QUERY_SUMMARY,
        DB_NAMESPACE,
        NETWORK_PEER_ADDRESS,
        NETWORK_PEER_PORT,
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
        "io.opentelemetry.clickhouse-client-v2-0.8",
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
          "io.opentelemetry.clickhouse-client-v2-0.8",
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
  void testAsyncFailurePreservesLegacyCompletion() {
    assumeFalse(emitStableDatabaseSemconv());

    Client testClient =
        new Client.Builder()
            .addEndpoint(Protocol.HTTP, host, port, false)
            .setDefaultDatabase(DATABASE_NAME)
            .setUsername(USERNAME)
            .setPassword(PASSWORD)
            .setOption("compress", "false")
            .useAsyncRequests(true)
            .build();
    cleanup.deferCleanup(testClient);

    CompletableFuture<QueryResponse> future = testClient.query("select * from non_existent_table");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SELECT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasStatus(StatusData.unset())
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_STATEMENT), "select * from non_existent_table"),
                            equalTo(maybeStable(DB_OPERATION), "SELECT"))));

    assertThat(catchThrowable(future::join)).isNotNull();
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
                                "insert into " + TABLE_NAME + " values(?)"),
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
                                "select * from " + TABLE_NAME + " where value={param_s: String}"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "select test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"))));
  }

  @Test
  void testMultipleEndpointsReportCanonicalConfiguredTarget() throws Exception {
    // the two endpoints have to stay distinct, and the container host is 127.0.0.1 on some Docker
    // configurations
    String secondHost = "127.0.0.1".equals(host) ? "localhost" : "127.0.0.1";
    String secondEndpoint = "http://" + secondHost + ":" + port;
    Client client =
        new Client.Builder()
            .addEndpoint(Protocol.HTTP, host, port, false)
            .addEndpoint(secondEndpoint)
            .setDefaultDatabase(DATABASE_NAME)
            .setUsername(USERNAME)
            .setPassword(PASSWORD)
            .setOption("compress", "false")
            .build();
    cleanup.deferCleanup(client);

    List<String> endpoints = new ArrayList<>(asList(host + ":" + port, secondHost + ":" + port));
    endpoints.sort(String::compareTo);
    String addressGroup = String.join(",", endpoints);
    String firstEndpoint = client.getEndpoints().iterator().next();
    String legacyAddress = UrlParser.getHost(firstEndpoint);
    Integer legacyPort = UrlParser.getPort(firstEndpoint);
    Object selectedEndpoint = selectedEndpoint(client);
    String peerAddress = endpointHost(selectedEndpoint);
    int peerPort = endpointPort(selectedEndpoint);

    QueryResponse response = client.query("select * from " + TABLE_NAME).join();
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
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv() ? addressGroup : legacyAddress),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv() ? null : (long) legacyPort),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv() ? peerAddress : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) peerPort : null),
                            equalTo(maybeStable(DB_STATEMENT), "select * from " + TABLE_NAME),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "select test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"))));
  }

  @Test
  void testRetryReportsSelectedEndpoint() throws Exception {
    assumeTrue(isClassPresent("com.clickhouse.client.api.transport.ClientNodeSelector"));

    int unavailablePort = 1;
    Client testClient =
        new Client.Builder()
            .addEndpoint("http://127.0.0.1:" + unavailablePort)
            .addEndpoint(Protocol.HTTP, host, port, false)
            .setDefaultDatabase(DATABASE_NAME)
            .setUsername(USERNAME)
            .setPassword(PASSWORD)
            .setOption("compress", "false")
            .setConnectTimeout(100)
            .setMaxRetries(1)
            .useAsyncRequests(true)
            .build();
    cleanup.deferCleanup(testClient);

    List<String> configuredAddresses =
        new ArrayList<>(asList("127.0.0.1:" + unavailablePort, host + ":" + port));
    configuredAddresses.sort(String::compareTo);
    putUnreachableEndpointFirst(testClient, unavailablePort);
    String currentEndpoint = testClient.getEndpoints().iterator().next();
    String legacyAddress = UrlParser.getHost(currentEndpoint);
    Integer legacyPort = UrlParser.getPort(currentEndpoint);

    QueryResponse response = testClient.query("select * from " + TABLE_NAME).join();
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
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? String.join(",", configuredAddresses)
                                    : legacyAddress),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv() ? null : (long) legacyPort),
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
  void testExhaustedRetryReportsAttemptedEndpoint() throws Exception {
    assumeTrue(isClassPresent("com.clickhouse.client.api.transport.ClientNodeSelector"));

    int firstUnavailablePort = 1;
    int secondUnavailablePort = 2;
    Client testClient =
        new Client.Builder()
            .addEndpoint("http://127.0.0.1:" + firstUnavailablePort)
            .addEndpoint("http://127.0.0.1:" + secondUnavailablePort)
            .setDefaultDatabase(DATABASE_NAME)
            .setUsername(USERNAME)
            .setPassword(PASSWORD)
            .setOption("compress", "false")
            .setConnectTimeout(100)
            .setMaxRetries(1)
            .useAsyncRequests(true)
            .build();
    cleanup.deferCleanup(testClient);

    List<String> configuredAddresses =
        new ArrayList<>(
            asList("127.0.0.1:" + firstUnavailablePort, "127.0.0.1:" + secondUnavailablePort));
    configuredAddresses.sort(String::compareTo);
    putUnreachableEndpointFirst(testClient, firstUnavailablePort);
    String currentEndpoint = testClient.getEndpoints().iterator().next();
    String legacyAddress = UrlParser.getHost(currentEndpoint);
    Integer legacyPort = UrlParser.getPort(currentEndpoint);

    Throwable thrown = catchThrowable(() -> testClient.query("select * from " + TABLE_NAME).join());
    assertThat(thrown).isNotNull();

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
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? String.join(",", configuredAddresses)
                                    : legacyAddress),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv() || legacyPort == null
                                    ? null
                                    : (long) legacyPort),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv() ? "127.0.0.1" : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) secondUnavailablePort : null),
                            equalTo(maybeStable(DB_STATEMENT), "select * from " + TABLE_NAME),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "select test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"),
                            equalTo(
                                ERROR_TYPE,
                                emitStableDatabaseSemconv()
                                    ? thrown.getCause().getClass().getName()
                                    : null))));
  }

  @Test
  void testConfiguredHttpAndHttpsDefaultPortsAreOmitted() throws Exception {
    Client httpClient =
        new Client.Builder()
            .addEndpoint("http://http.example:8123")
            .setUsername(USERNAME)
            .setPassword(PASSWORD)
            .build();
    Client httpsClient =
        new Client.Builder()
            .addEndpoint("https://https.example:8443")
            .setUsername(USERNAME)
            .setPassword(PASSWORD)
            .build();
    Client multiClient =
        new Client.Builder()
            .addEndpoint("http://http.example:8123")
            .addEndpoint("https://https.example:8443")
            .setUsername(USERNAME)
            .setPassword(PASSWORD)
            .build();
    cleanup.deferCleanup(httpClient);
    cleanup.deferCleanup(httpsClient);
    cleanup.deferCleanup(multiClient);

    assertServerInfo(httpClient, "http.example", null, null);
    assertServerInfo(httpsClient, "https.example", null, null);
    assertServerInfo(multiClient, null, null, "http.example,https.example");
  }

  @Test
  void testConfiguredEndpointsInlineSharedNonDefaultPort() throws Exception {
    Client testClient =
        new Client.Builder()
            .addEndpoint("http://host2.example:9123")
            .addEndpoint("https://host1.example:9123")
            .setUsername(USERNAME)
            .setPassword(PASSWORD)
            .build();
    cleanup.deferCleanup(testClient);

    assertServerInfo(testClient, null, null, "host1.example:9123,host2.example:9123");
  }

  @Test
  void testConfiguredEndpointsInlineMixedPortsAndBracketIpv6() throws Exception {
    Client testClient =
        new Client.Builder()
            .addEndpoint("http://host.example:8123")
            .addEndpoint("https://[2001:db8::1]:9443")
            .setUsername(USERNAME)
            .setPassword(PASSWORD)
            .build();
    cleanup.deferCleanup(testClient);

    assertServerInfo(testClient, null, null, "[2001:db8::1]:9443,host.example:8123");
  }

  @Test
  void testConfiguredIpv6EndpointsOnDefaultPortsRemainUnbracketed() throws Exception {
    Client testClient =
        new Client.Builder()
            .addEndpoint("http://[2001:db8::2]:8123")
            .addEndpoint("https://[2001:db8::1]:8443")
            .setUsername(USERNAME)
            .setPassword(PASSWORD)
            .build();
    cleanup.deferCleanup(testClient);

    assertServerInfo(testClient, null, null, "2001:db8::1,2001:db8::2");
  }

  @Test
  void testConfiguredEndpointsIncludeAtMostFiveEndpoints() throws Exception {
    Set<String> fiveEndpoints =
        new HashSet<>(
            asList(
                "http://host5.example:8123",
                "http://host3.example:8123",
                "http://host1.example:8123",
                "http://host4.example:8123",
                "http://host2.example:8123"));
    Set<String> sixEndpoints = new HashSet<>(fiveEndpoints);
    sixEndpoints.add("http://host6.example:8123");
    String expected = "host1.example,host2.example,host3.example,host4.example,host5.example";

    assertServerInfo(fiveEndpoints, null, null, expected);
    assertServerInfo(sixEndpoints, null, null, expected);
  }

  @Test
  void testConfiguredEndpointsUsePortModeFromAllEndpoints() throws Exception {
    Set<String> endpoints =
        new HashSet<>(
            asList(
                "http://host1.example:8123",
                "http://host2.example:8123",
                "http://host3.example:8123",
                "http://host4.example:8123",
                "http://host5.example:8123",
                "http://host6.example:9123"));

    assertServerInfo(
        endpoints,
        null,
        null,
        "host1.example:8123,host2.example:8123,host3.example:8123,"
            + "host4.example:8123,host5.example:8123");
  }

  @Test
  void testConfiguredEndpointsValidateEndpointsAfterTheLimit() throws Exception {
    Set<String> endpoints =
        new HashSet<>(
            asList(
                "http://host1.example:8123",
                "http://host2.example:8123",
                "http://host3.example:8123",
                "http://host4.example:8123",
                "http://host5.example:8123",
                "http://host6.example:not-a-port"));

    assertServerInfo(endpoints, null, null, null);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://first.example,second.example:8123",
        " http://host.example:8123",
        "http://host.example:8123 "
      })
  void testConfiguredEndpointsRejectAmbiguousAuthorities(String endpoint) throws Exception {
    assertServerInfo(new HashSet<>(asList(endpoint)), null, null, null);
  }

  @Test
  void testConfiguredAndCurrentEndpointsAreCapturedSeparately() throws Exception {
    Client testClient =
        new Client.Builder()
            .addEndpoint(Protocol.HTTP, host, port, false)
            .setDefaultDatabase(DATABASE_NAME)
            .setUsername(USERNAME)
            .setPassword(PASSWORD)
            .setOption("compress", "false")
            .useAsyncRequests(true)
            .build();
    cleanup.deferCleanup(testClient);
    String currentHost = "127.0.0.1".equals(host) ? "localhost" : "127.0.0.1";
    replaceEndpoints(testClient, "http://" + currentHost + ":" + port);
    Object selectedEndpoint = selectedEndpoint(testClient);
    String peerAddress = endpointHost(selectedEndpoint);
    int peerPort = endpointPort(selectedEndpoint);

    QueryResponse response = testClient.query("select * from " + TABLE_NAME).join();
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
                            equalTo(
                                SERVER_ADDRESS, emitStableDatabaseSemconv() ? host : currentHost),
                            equalTo(SERVER_PORT, port),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv() ? peerAddress : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) peerPort : null),
                            equalTo(maybeStable(DB_STATEMENT), "select * from " + TABLE_NAME),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "select test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"))));
  }

  @Test
  void testMissingSnapshotRetainsOnlyLegacyEndpoint() throws Exception {
    Client testClient =
        new Client.Builder()
            .addEndpoint(Protocol.HTTP, host, port, false)
            .setDefaultDatabase(DATABASE_NAME)
            .setUsername(USERNAME)
            .setPassword(PASSWORD)
            .setOption("compress", "false")
            .build();
    cleanup.deferCleanup(testClient);
    assertThat(clearServerInfo(testClient)).isNull();

    QueryResponse response = testClient.query("select * from " + TABLE_NAME).join();
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
                            equalTo(SERVER_PORT, emitStableDatabaseSemconv() ? null : (long) port),
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
  void testMultipleEndpointsRejectCredentials() throws Exception {
    List<String> endpoints =
        new ArrayList<>(
            asList(
                "https://user:secret@[2001:db8::1]:8443/database?option=value#fragment",
                "http://host.example:8123"));

    Client.Builder builder =
        new Client.Builder()
            .setUsername(USERNAME)
            .setPassword(PASSWORD)
            .setConnectTimeout(100)
            .setMaxRetries(0);
    for (String endpoint : endpoints) {
      builder.addEndpoint(endpoint);
    }
    Client testClient = builder.build();
    cleanup.deferCleanup(testClient);
    replaceServerInfo(testClient, endpoints.toArray(new String[0]));
    String currentEndpoint = testClient.getEndpoints().iterator().next();
    String legacyAddress = UrlParser.getHost(currentEndpoint);
    Integer legacyPort = UrlParser.getPort(currentEndpoint);
    Object selectedEndpoint = selectedEndpoint(testClient);
    String peerAddress = endpointHost(selectedEndpoint);
    int peerPort = endpointPort(selectedEndpoint);

    Throwable thrown = catchThrowable(() -> testClient.query("select 1").join());
    assertThat(thrown).isNotNull();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(
                                SERVER_ADDRESS, emitStableDatabaseSemconv() ? null : legacyAddress),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv() || legacyPort == null
                                    ? null
                                    : (long) legacyPort),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv() ? peerAddress : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) peerPort : null),
                            equalTo(maybeStable(DB_STATEMENT), "select ?"),
                            equalTo(
                                DB_QUERY_SUMMARY, emitStableDatabaseSemconv() ? "select" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"),
                            equalTo(
                                ERROR_TYPE,
                                emitStableDatabaseSemconv()
                                    ? thrown.getClass().getName()
                                    : null))));
  }

  @Test
  void testSingleEndpointExcludesUrlComponents() {
    Client testClient =
        new Client.Builder()
            .addEndpoint("http://[2001:db8::1]:8443/database?option=value#fragment")
            .setUsername(USERNAME)
            .setPassword(PASSWORD)
            .setConnectTimeout(100)
            .setMaxRetries(0)
            .build();
    cleanup.deferCleanup(testClient);

    Throwable thrown = catchThrowable(() -> testClient.query("select 1").join());
    assertThat(thrown).isNotNull();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv() ? "2001:db8::1" : "[2001"),
                            equalTo(SERVER_PORT, emitStableDatabaseSemconv() ? 8443L : null),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv() ? "2001:db8::1" : null),
                            equalTo(NETWORK_PEER_PORT, emitStableDatabaseSemconv() ? 8443L : null),
                            equalTo(maybeStable(DB_STATEMENT), "select ?"),
                            equalTo(
                                DB_QUERY_SUMMARY, emitStableDatabaseSemconv() ? "select" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"),
                            equalTo(
                                ERROR_TYPE,
                                emitStableDatabaseSemconv()
                                    ? thrown.getClass().getName()
                                    : null))));
  }

  @Test
  void testProgrammaticEndpointRejectsEncodedSensitiveOptions() throws Exception {
    Client testClient =
        new Client.Builder()
            .addEndpoint(Protocol.HTTP, host, port, false)
            .setDefaultDatabase(DATABASE_NAME)
            .setUsername(USERNAME)
            .setPassword(PASSWORD)
            .setOption("compress", "false")
            .build();
    cleanup.deferCleanup(testClient);
    String currentEndpoint = testClient.getEndpoints().iterator().next();
    String legacyAddress = UrlParser.getHost(currentEndpoint);
    Integer legacyPort = UrlParser.getPort(currentEndpoint);
    String peerAddress = endpointHost(currentEndpoint);
    int peerPort = URI.create(currentEndpoint).getPort();
    replaceServerInfo(testClient, "http://configured.example%3fpassword%3dsecret:8123");

    QueryResponse response = testClient.query("select 1").join();
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
                                SERVER_ADDRESS, emitStableDatabaseSemconv() ? null : legacyAddress),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv() || legacyPort == null
                                    ? null
                                    : (long) legacyPort),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv() ? peerAddress : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) peerPort : null),
                            equalTo(maybeStable(DB_STATEMENT), "select ?"),
                            equalTo(
                                DB_QUERY_SUMMARY, emitStableDatabaseSemconv() ? "select" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"))));
  }

  private static void replaceEndpoints(Client client, String endpoint) throws Exception {
    Field endpointsField = Client.class.getDeclaredField("endpoints");
    endpointsField.setAccessible(true);
    try (Client replacementClient =
        new Client.Builder()
            .addEndpoint(endpoint)
            .setUsername(USERNAME)
            .setPassword(PASSWORD)
            .build()) {
      endpointsField.set(client, endpointsField.get(replacementClient));
    }
  }

  private static void replaceServerInfo(Client client, String... endpoints) throws Exception {
    Class<?> singletons = singletons(client);
    Class<?> serverInfoClass = serverInfo(client).getClass();
    Method of = serverInfoClass.getDeclaredMethod("of", Set.class);
    of.setAccessible(true);
    Object replacement = of.invoke(null, new HashSet<>(asList(endpoints)));

    Field serverInfoField = singletons.getDeclaredField("SERVER_INFO_FIELD");
    serverInfoField.setAccessible(true);
    Object virtualField = serverInfoField.get(null);
    virtualField
        .getClass()
        .getMethod("set", Object.class, Object.class)
        .invoke(virtualField, client, replacement);
  }

  private static Object serverInfo(Client client) throws Exception {
    return singletons(client).getMethod("serverInfo", Client.class).invoke(null, client);
  }

  private static Object clearServerInfo(Client client) throws Exception {
    Class<?> singletons = singletons(client);
    Method clearServerInfo = singletons.getDeclaredMethod("clearServerInfo", Client.class);
    clearServerInfo.setAccessible(true);
    clearServerInfo.invoke(null, client);
    return serverInfo(client);
  }

  private static void assertServerInfo(
      Client client, String address, Integer port, String addressGroup) throws Exception {
    assertServerInfo(serverInfo(client), address, port, addressGroup);
  }

  private static void assertServerInfo(
      Set<String> endpoints, String address, Integer port, String addressGroup) throws Exception {
    Class<?> serverInfoClass = serverInfo(client).getClass();
    Method of = serverInfoClass.getDeclaredMethod("of", Set.class);
    of.setAccessible(true);
    assertServerInfo(of.invoke(null, endpoints), address, port, addressGroup);
  }

  private static void assertServerInfo(
      Object serverInfo, String address, Integer port, String addressGroup) throws Exception {
    Class<?> serverInfoClass = serverInfo.getClass();
    assertThat(serverInfoClass.getMethod("getAddress").invoke(serverInfo)).isEqualTo(address);
    assertThat(serverInfoClass.getMethod("getPort").invoke(serverInfo)).isEqualTo(port);
    assertThat(serverInfoClass.getMethod("getAddressGroup").invoke(serverInfo))
        .isEqualTo(addressGroup);
  }

  private static String endpointHost(String endpoint) {
    String host = URI.create(endpoint).getHost();
    return host.startsWith("[") ? host.substring(1, host.length() - 1) : host;
  }

  private static String endpointHost(Object endpoint) throws Exception {
    String host = (String) endpoint.getClass().getMethod("getHost").invoke(endpoint);
    return host.startsWith("[") ? host.substring(1, host.length() - 1) : host;
  }

  private static Object selectedEndpoint(Client client) throws Exception {
    try {
      Field selectorField = Client.class.getDeclaredField("nodeSelector");
      selectorField.setAccessible(true);
      Object selector = selectorField.get(client);
      return selector.getClass().getMethod("getEndpoint").invoke(selector);
    } catch (NoSuchFieldException ignored) {
      Field endpointsField;
      try {
        endpointsField = Client.class.getDeclaredField("serverNodes");
      } catch (NoSuchFieldException ignore) {
        endpointsField = Client.class.getDeclaredField("endpoints");
      }
      endpointsField.setAccessible(true);
      return ((List<?>) endpointsField.get(client)).get(0);
    }
  }

  private static int endpointPort(Object endpoint) throws Exception {
    return (Integer) endpoint.getClass().getMethod("getPort").invoke(endpoint);
  }

  private static boolean isClassPresent(String className) {
    try {
      Class.forName(className, false, Client.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException ignored) {
      return false;
    }
  }

  private static void putUnreachableEndpointFirst(Client client, int unavailablePort)
      throws Exception {
    Field endpointsField = Client.class.getDeclaredField("endpoints");
    endpointsField.setAccessible(true);
    List<?> endpoints = (List<?>) endpointsField.get(client);
    List<Object> orderedEndpoints = new ArrayList<>(endpoints.size());
    Object unavailableEndpoint = null;
    for (Object endpoint : endpoints) {
      int endpointPort = (Integer) endpoint.getClass().getMethod("getPort").invoke(endpoint);
      if (endpointPort == unavailablePort) {
        unavailableEndpoint = endpoint;
      } else {
        orderedEndpoints.add(endpoint);
      }
    }
    assertThat(unavailableEndpoint).isNotNull();
    orderedEndpoints.add(0, unavailableEndpoint);
    endpointsField.set(client, orderedEndpoints);

    Class<?> selectorClass =
        Class.forName(
            "com.clickhouse.client.api.transport.ClientNodeSelector",
            true,
            Client.class.getClassLoader());
    Object selector = selectorClass.getConstructor(List.class).newInstance(orderedEndpoints);
    Field selectorField = Client.class.getDeclaredField("nodeSelector");
    selectorField.setAccessible(true);
    selectorField.set(client, selector);
  }

  private static Class<?> singletons(Client client) throws Exception {
    String singletonsName =
        "io.opentelemetry.javaagent.instrumentation.clickhouse.clientv2.v0_8."
            + "ClickHouseClientV2Singletons";
    ClassLoader clientClassLoader = client.getClass().getClassLoader();
    try {
      return Class.forName(singletonsName, true, clientClassLoader);
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
                  "io.opentelemetry.javaagent.instrumentation.clickhouse.clientv2.v0_8."
                      + "ClickHouseClientV2InstrumentationModule",
                  clientClassLoader);
      return Class.forName(singletonsName, true, instrumentationClassLoader);
    }
  }
}
