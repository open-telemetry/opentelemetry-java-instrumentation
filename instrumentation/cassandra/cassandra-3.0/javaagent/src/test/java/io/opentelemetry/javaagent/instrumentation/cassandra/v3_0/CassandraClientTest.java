/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_CONSISTENCY_LEVEL;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_COORDINATOR_DC;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_COORDINATOR_ID;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_IDEMPOTENCE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_PAGE_SIZE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_TABLE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.CASSANDRA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.ExecutionInfo;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.exceptions.InvalidQueryException;
import com.datastax.driver.core.exceptions.SyntaxError;
import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

@SuppressWarnings("deprecation") // using deprecated semconv
class CassandraClientTest {

  private static final Logger logger = LoggerFactory.getLogger(CassandraClientTest.class);

  private static final ExecutorService executor = Executors.newCachedThreadPool();

  private static final boolean speculativeExecutionCountAvailable = hasSpeculativeExecutionCount();
  private static final boolean coordinatorIdAvailable = hasCoordinatorId();

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static GenericContainer<?> cassandra;

  private static String cassandraHost;

  private static String cassandraIp;
  private static int cassandraPort;
  private static Cluster cluster;

  @BeforeAll
  static void beforeAll() throws UnknownHostException {
    cassandra =
        new GenericContainer<>("cassandra:3")
            .withEnv("JVM_OPTS", "-Xmx128m -Xms128m")
            // speed up single-node startup
            .withEnv(
                "JVM_EXTRA_OPTS",
                "-Dcassandra.skip_wait_for_gossip_to_settle=0 -Dcassandra.initial_token=0")
            .withExposedPorts(9042)
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .withStartupTimeout(Duration.ofMinutes(2));
    cleanup.deferAfterAll(cassandra::stop);
    cassandra.start();

    cassandraHost = cassandra.getHost();
    cassandraIp = InetAddress.getByName(cassandra.getHost()).getHostAddress();
    cassandraPort = cassandra.getMappedPort(9042);
    cluster =
        Cluster.builder()
            .addContactPointsWithPorts(new InetSocketAddress(cassandra.getHost(), cassandraPort))
            .build();
    cleanup.deferAfterAll(cluster);
    cleanup.deferAfterAll(() -> executor.shutdownNow());
  }

  @Test
  void shouldEmitRequestAttributesWhenExecutionFails() {
    Session session = cluster.connect();
    cleanup.deferCleanup(session);
    SimpleStatement statement = new SimpleStatement("SELECT * FROM missing_table");
    statement.setConsistencyLevel(ConsistencyLevel.ONE);
    statement.setFetchSize(123);
    statement.setIdempotent(true);

    Throwable thrown = catchThrowable(() -> session.execute(statement));

    assertThat(thrown).isInstanceOf(InvalidQueryException.class);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SELECT missing_table")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(thrown)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitStableDatabaseSemconv() ? null : "ipv4"),
                            equalTo(SERVER_ADDRESS, cassandraHost),
                            equalTo(SERVER_PORT, cassandraPort),
                            equalTo(NETWORK_PEER_ADDRESS, cassandraIp),
                            equalTo(NETWORK_PEER_PORT, cassandraPort),
                            equalTo(maybeStable(DB_SYSTEM), CASSANDRA),
                            equalTo(maybeStable(DB_STATEMENT), "SELECT * FROM missing_table"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "SELECT missing_table" : null),
                            equalTo(maybeStable(DB_OPERATION), "SELECT"),
                            equalTo(maybeStable(DB_CASSANDRA_TABLE), "missing_table"),
                            equalTo(maybeStable(DB_CASSANDRA_CONSISTENCY_LEVEL), "ONE"),
                            equalTo(maybeStable(DB_CASSANDRA_IDEMPOTENCE), true),
                            equalTo(maybeStable(DB_CASSANDRA_PAGE_SIZE), 123),
                            equalTo(
                                ERROR_TYPE,
                                emitStableDatabaseSemconv()
                                    ? InvalidQueryException.class.getName()
                                    : null))));
  }

  @Test
  void shouldOmitPageSizeWhenPagingIsDisabled() {
    Session session = cluster.connect();
    cleanup.deferCleanup(session);
    SimpleStatement statement = new SimpleStatement("SELECT * FROM missing_table");
    statement.setConsistencyLevel(ConsistencyLevel.ONE);
    // the driver treats Integer.MAX_VALUE as a request to disable paging
    statement.setFetchSize(Integer.MAX_VALUE);
    statement.setIdempotent(true);

    Throwable thrown = catchThrowable(() -> session.execute(statement));

    assertThat(thrown).isInstanceOf(InvalidQueryException.class);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SELECT missing_table")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(thrown)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitStableDatabaseSemconv() ? null : "ipv4"),
                            equalTo(SERVER_ADDRESS, cassandraHost),
                            equalTo(SERVER_PORT, cassandraPort),
                            equalTo(NETWORK_PEER_ADDRESS, cassandraIp),
                            equalTo(NETWORK_PEER_PORT, cassandraPort),
                            equalTo(maybeStable(DB_SYSTEM), CASSANDRA),
                            equalTo(maybeStable(DB_STATEMENT), "SELECT * FROM missing_table"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "SELECT missing_table" : null),
                            equalTo(maybeStable(DB_OPERATION), "SELECT"),
                            equalTo(maybeStable(DB_CASSANDRA_TABLE), "missing_table"),
                            equalTo(maybeStable(DB_CASSANDRA_CONSISTENCY_LEVEL), "ONE"),
                            equalTo(maybeStable(DB_CASSANDRA_IDEMPOTENCE), true),
                            equalTo(
                                ERROR_TYPE,
                                emitStableDatabaseSemconv()
                                    ? InvalidQueryException.class.getName()
                                    : null))));
  }

  @Test
  void singleConfiguredContactPointIsStableTarget() {
    Cluster namedContactPointCluster =
        Cluster.builder().addContactPoint("LOCALHOST").withPort(cassandraPort).build();

    assertConfiguredTarget(namedContactPointCluster, "LOCALHOST", cassandraPort);
  }

  @Test
  void multipleConfiguredContactPointsWithSharedNonDefaultPortAreStableTarget() {
    Cluster multiContactPointCluster =
        Cluster.builder()
            .addContactPoints(cassandraHost, "127.0.0.2")
            .withPort(cassandraPort)
            .build();

    String expectedAddress = cassandraHost + ":" + cassandraPort + ",127.0.0.2:" + cassandraPort;
    assertConfiguredTarget(multiContactPointCluster, expectedAddress, null);
  }

  @Test
  void multipleConfiguredContactPointsWithMixedPortsAreStableTarget() {
    Cluster multiContactPointCluster =
        Cluster.builder()
            .addContactPointsWithPorts(
                new InetSocketAddress(cassandraHost, cassandraPort),
                // unreachable on purpose: only the configuration is under test
                new InetSocketAddress("127.0.0.2", 9042))
            .build();

    String expectedAddress = cassandraHost + ":" + cassandraPort + ",127.0.0.2:9042";
    assertConfiguredTarget(multiContactPointCluster, expectedAddress, null);
  }

  @Test
  void emptyConfiguredContactPointDropsTarget() {
    Cluster emptyContactPointCluster =
        Cluster.builder().addContactPoints(cassandraHost, "").withPort(cassandraPort).build();

    assertConfiguredTarget(emptyContactPointCluster, null, null);
  }

  @Test
  void exceptionalPartialContactPointMutationDropsTarget() {
    Cluster.Builder builder = Cluster.builder();
    String[] contactPoints = {"127.0.0.2", null};
    assertThatThrownBy(() -> builder.addContactPoints(contactPoints))
        .isInstanceOf(NullPointerException.class);

    Cluster configuredCluster =
        builder.addContactPoint(cassandraHost).withPort(cassandraPort).build();

    assertConfiguredTarget(configuredCluster, null, null);
  }

  private static void assertConfiguredTarget(
      Cluster configuredCluster, String expectedAddress, Integer expectedPort) {
    cleanup.deferCleanup(configuredCluster);
    Session session = configuredCluster.connect();
    cleanup.deferCleanup(session);

    ResultSet resultSet = session.execute("DROP KEYSPACE IF EXISTS contact_points_test");
    InetSocketAddress coordinatorAddress =
        resultSet.getExecutionInfo().getQueriedHost().getSocketAddress();
    Long expectedServerPort =
        emitStableDatabaseSemconv()
            ? (expectedPort == null ? null : Long.valueOf(expectedPort))
            : Long.valueOf(coordinatorAddress.getPort());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "DROP KEYSPACE" : "DROP")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                NETWORK_TYPE,
                                emitStableDatabaseSemconv()
                                    ? val -> val.isNull()
                                    : val -> val.isIn("ipv4", "ipv6")),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? expectedAddress
                                    : coordinatorAddress.getHostString()),
                            equalTo(SERVER_PORT, expectedServerPort),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                coordinatorAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, coordinatorAddress.getPort()),
                            equalTo(maybeStable(DB_SYSTEM), CASSANDRA),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "DROP KEYSPACE IF EXISTS contact_points_test"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "DROP KEYSPACE" : null),
                            equalTo(maybeStable(DB_OPERATION), "DROP"),
                            equalTo(maybeStable(DB_CASSANDRA_CONSISTENCY_LEVEL), "LOCAL_ONE"),
                            equalTo(maybeStable(DB_CASSANDRA_COORDINATOR_DC), "datacenter1"),
                            satisfies(
                                maybeStable(DB_CASSANDRA_COORDINATOR_ID),
                                coordinatorIdAvailable
                                    ? val -> val.isInstanceOf(String.class)
                                    : val -> val.isNull()),
                            equalTo(maybeStable(DB_CASSANDRA_IDEMPOTENCE), false),
                            equalTo(maybeStable(DB_CASSANDRA_PAGE_SIZE), 5000),
                            satisfies(
                                maybeStable(DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT),
                                speculativeExecutionCountAvailable
                                    ? val -> val.isEqualTo(0)
                                    : val -> val.isNull()))));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("provideSyncParameters")
  void syncTest(Parameter parameter) {
    Session session = cluster.connect(parameter.keyspace);
    cleanup.deferCleanup(session);

    session.execute(parameter.queryText);

    if (parameter.keyspace != null) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName(
                              emitStableDatabaseSemconv()
                                  ? "USE " + parameter.keyspace
                                  : "DB Query")
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(
                              equalTo(NETWORK_TYPE, emitStableDatabaseSemconv() ? null : "ipv4"),
                              equalTo(SERVER_ADDRESS, cassandraHost),
                              equalTo(SERVER_PORT, cassandraPort),
                              equalTo(NETWORK_PEER_ADDRESS, cassandraIp),
                              equalTo(NETWORK_PEER_PORT, cassandraPort),
                              equalTo(maybeStable(DB_SYSTEM), CASSANDRA),
                              equalTo(maybeStable(DB_STATEMENT), "USE " + parameter.keyspace),
                              equalTo(
                                  maybeStable(DB_OPERATION),
                                  emitStableDatabaseSemconv() ? "USE" : null),
                              equalTo(
                                  DB_QUERY_SUMMARY,
                                  emitStableDatabaseSemconv() ? "USE " + parameter.keyspace : null),
                              equalTo(maybeStable(DB_CASSANDRA_CONSISTENCY_LEVEL), "LOCAL_ONE"),
                              equalTo(maybeStable(DB_CASSANDRA_COORDINATOR_DC), "datacenter1"),
                              satisfies(
                                  maybeStable(DB_CASSANDRA_COORDINATOR_ID),
                                  coordinatorIdAvailable
                                      ? val -> val.isInstanceOf(String.class)
                                      : val -> val.isNull()),
                              equalTo(maybeStable(DB_CASSANDRA_IDEMPOTENCE), false),
                              equalTo(maybeStable(DB_CASSANDRA_PAGE_SIZE), 5000),
                              satisfies(
                                  maybeStable(DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT),
                                  speculativeExecutionCountAvailable
                                      ? val -> val.isEqualTo(0)
                                      : val -> val.isNull()))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName(parameter.spanName)
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(
                              equalTo(NETWORK_TYPE, emitStableDatabaseSemconv() ? null : "ipv4"),
                              equalTo(SERVER_ADDRESS, cassandraHost),
                              equalTo(SERVER_PORT, cassandraPort),
                              equalTo(NETWORK_PEER_ADDRESS, cassandraIp),
                              equalTo(NETWORK_PEER_PORT, cassandraPort),
                              equalTo(maybeStable(DB_SYSTEM), CASSANDRA),
                              equalTo(maybeStable(DB_NAME), parameter.keyspace),
                              equalTo(maybeStable(DB_STATEMENT), parameter.expectedQueryText),
                              equalTo(
                                  DB_QUERY_SUMMARY,
                                  emitStableDatabaseSemconv() ? parameter.spanName : null),
                              equalTo(maybeStable(DB_OPERATION), parameter.operation),
                              equalTo(maybeStable(DB_CASSANDRA_CONSISTENCY_LEVEL), "LOCAL_ONE"),
                              equalTo(maybeStable(DB_CASSANDRA_COORDINATOR_DC), "datacenter1"),
                              satisfies(
                                  maybeStable(DB_CASSANDRA_COORDINATOR_ID),
                                  coordinatorIdAvailable
                                      ? val -> val.isInstanceOf(String.class)
                                      : val -> val.isNull()),
                              equalTo(maybeStable(DB_CASSANDRA_IDEMPOTENCE), false),
                              equalTo(maybeStable(DB_CASSANDRA_PAGE_SIZE), 5000),
                              satisfies(
                                  maybeStable(DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT),
                                  speculativeExecutionCountAvailable
                                      ? val -> val.isEqualTo(0)
                                      : val -> val.isNull()),
                              equalTo(maybeStable(DB_CASSANDRA_TABLE), parameter.table))));
    } else {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName(parameter.spanName)
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(
                              equalTo(NETWORK_TYPE, emitStableDatabaseSemconv() ? null : "ipv4"),
                              equalTo(SERVER_ADDRESS, cassandraHost),
                              equalTo(SERVER_PORT, cassandraPort),
                              equalTo(NETWORK_PEER_ADDRESS, cassandraIp),
                              equalTo(NETWORK_PEER_PORT, cassandraPort),
                              equalTo(maybeStable(DB_SYSTEM), CASSANDRA),
                              equalTo(maybeStable(DB_STATEMENT), parameter.expectedQueryText),
                              equalTo(
                                  DB_QUERY_SUMMARY,
                                  emitStableDatabaseSemconv() ? parameter.spanName : null),
                              equalTo(maybeStable(DB_OPERATION), parameter.operation),
                              equalTo(maybeStable(DB_CASSANDRA_CONSISTENCY_LEVEL), "LOCAL_ONE"),
                              equalTo(maybeStable(DB_CASSANDRA_COORDINATOR_DC), "datacenter1"),
                              satisfies(
                                  maybeStable(DB_CASSANDRA_COORDINATOR_ID),
                                  coordinatorIdAvailable
                                      ? val -> val.isInstanceOf(String.class)
                                      : val -> val.isNull()),
                              equalTo(maybeStable(DB_CASSANDRA_IDEMPOTENCE), false),
                              equalTo(maybeStable(DB_CASSANDRA_PAGE_SIZE), 5000),
                              satisfies(
                                  maybeStable(DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT),
                                  speculativeExecutionCountAvailable
                                      ? val -> val.isEqualTo(0)
                                      : val -> val.isNull()),
                              equalTo(maybeStable(DB_CASSANDRA_TABLE), parameter.table))));
    }
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("provideAsyncParameters")
  void asyncTest(Parameter parameter) {
    Session session = cluster.connect(parameter.keyspace);
    cleanup.deferCleanup(session);

    testing.runWithSpan(
        "parent",
        () -> {
          ResultSetFuture future = session.executeAsync(parameter.queryText);
          future.addListener(() -> testing.runWithSpan("callbackListener", () -> {}), executor);
        });

    if (parameter.keyspace != null) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName(
                              emitStableDatabaseSemconv()
                                  ? "USE " + parameter.keyspace
                                  : "DB Query")
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(
                              equalTo(NETWORK_TYPE, emitStableDatabaseSemconv() ? null : "ipv4"),
                              equalTo(SERVER_ADDRESS, cassandraHost),
                              equalTo(SERVER_PORT, cassandraPort),
                              equalTo(NETWORK_PEER_ADDRESS, cassandraIp),
                              equalTo(NETWORK_PEER_PORT, cassandraPort),
                              equalTo(maybeStable(DB_SYSTEM), CASSANDRA),
                              equalTo(maybeStable(DB_STATEMENT), "USE " + parameter.keyspace),
                              equalTo(
                                  maybeStable(DB_OPERATION),
                                  emitStableDatabaseSemconv() ? "USE" : null),
                              equalTo(
                                  DB_QUERY_SUMMARY,
                                  emitStableDatabaseSemconv() ? "USE " + parameter.keyspace : null),
                              equalTo(maybeStable(DB_CASSANDRA_CONSISTENCY_LEVEL), "LOCAL_ONE"),
                              equalTo(maybeStable(DB_CASSANDRA_COORDINATOR_DC), "datacenter1"),
                              satisfies(
                                  maybeStable(DB_CASSANDRA_COORDINATOR_ID),
                                  coordinatorIdAvailable
                                      ? val -> val.isInstanceOf(String.class)
                                      : val -> val.isNull()),
                              equalTo(maybeStable(DB_CASSANDRA_IDEMPOTENCE), false),
                              equalTo(maybeStable(DB_CASSANDRA_PAGE_SIZE), 5000),
                              satisfies(
                                  maybeStable(DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT),
                                  speculativeExecutionCountAvailable
                                      ? val -> val.isEqualTo(0)
                                      : val -> val.isNull()))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                  span ->
                      span.hasName(parameter.spanName)
                          .hasKind(SpanKind.CLIENT)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(
                              equalTo(NETWORK_TYPE, emitStableDatabaseSemconv() ? null : "ipv4"),
                              equalTo(SERVER_ADDRESS, cassandraHost),
                              equalTo(SERVER_PORT, cassandraPort),
                              equalTo(NETWORK_PEER_ADDRESS, cassandraIp),
                              equalTo(NETWORK_PEER_PORT, cassandraPort),
                              equalTo(maybeStable(DB_SYSTEM), CASSANDRA),
                              equalTo(maybeStable(DB_NAME), parameter.keyspace),
                              equalTo(maybeStable(DB_STATEMENT), parameter.expectedQueryText),
                              equalTo(
                                  DB_QUERY_SUMMARY,
                                  emitStableDatabaseSemconv() ? parameter.spanName : null),
                              equalTo(maybeStable(DB_OPERATION), parameter.operation),
                              equalTo(maybeStable(DB_CASSANDRA_CONSISTENCY_LEVEL), "LOCAL_ONE"),
                              equalTo(maybeStable(DB_CASSANDRA_COORDINATOR_DC), "datacenter1"),
                              satisfies(
                                  maybeStable(DB_CASSANDRA_COORDINATOR_ID),
                                  coordinatorIdAvailable
                                      ? val -> val.isInstanceOf(String.class)
                                      : val -> val.isNull()),
                              equalTo(maybeStable(DB_CASSANDRA_IDEMPOTENCE), false),
                              equalTo(maybeStable(DB_CASSANDRA_PAGE_SIZE), 5000),
                              satisfies(
                                  maybeStable(DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT),
                                  speculativeExecutionCountAvailable
                                      ? val -> val.isEqualTo(0)
                                      : val -> val.isNull()),
                              equalTo(maybeStable(DB_CASSANDRA_TABLE), parameter.table)),
                  span ->
                      span.hasName("callbackListener")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(0))));
    } else {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                  span ->
                      span.hasName(parameter.spanName)
                          .hasKind(SpanKind.CLIENT)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(
                              equalTo(NETWORK_TYPE, emitStableDatabaseSemconv() ? null : "ipv4"),
                              equalTo(SERVER_ADDRESS, cassandraHost),
                              equalTo(SERVER_PORT, cassandraPort),
                              equalTo(NETWORK_PEER_ADDRESS, cassandraIp),
                              equalTo(NETWORK_PEER_PORT, cassandraPort),
                              equalTo(maybeStable(DB_SYSTEM), CASSANDRA),
                              equalTo(maybeStable(DB_STATEMENT), parameter.expectedQueryText),
                              equalTo(
                                  DB_QUERY_SUMMARY,
                                  emitStableDatabaseSemconv() ? parameter.spanName : null),
                              equalTo(maybeStable(DB_OPERATION), parameter.operation),
                              equalTo(maybeStable(DB_CASSANDRA_CONSISTENCY_LEVEL), "LOCAL_ONE"),
                              equalTo(maybeStable(DB_CASSANDRA_COORDINATOR_DC), "datacenter1"),
                              satisfies(
                                  maybeStable(DB_CASSANDRA_COORDINATOR_ID),
                                  coordinatorIdAvailable
                                      ? val -> val.isInstanceOf(String.class)
                                      : val -> val.isNull()),
                              equalTo(maybeStable(DB_CASSANDRA_IDEMPOTENCE), false),
                              equalTo(maybeStable(DB_CASSANDRA_PAGE_SIZE), 5000),
                              satisfies(
                                  maybeStable(DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT),
                                  speculativeExecutionCountAvailable
                                      ? val -> val.isEqualTo(0)
                                      : val -> val.isNull()),
                              equalTo(maybeStable(DB_CASSANDRA_TABLE), parameter.table)),
                  span ->
                      span.hasName("callbackListener")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(0))));
    }
  }

  @ParameterizedTest
  @MethodSource("simpleStatementScenarios")
  void simpleStatementSanitization(
      SimpleStatement statement, String stableQueryText, String legacyQueryText) {
    Session session = cluster.connect();
    cleanup.deferCleanup(session);

    session.execute("DROP KEYSPACE IF EXISTS simple_values_test");
    session.execute(
        "CREATE KEYSPACE simple_values_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':1}");
    session.execute("CREATE TABLE simple_values_test.users ( name text PRIMARY KEY, age int )");
    testing.clearData();

    session.execute(statement);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("INSERT simple_values_test.users")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitStableDatabaseSemconv() ? null : "ipv4"),
                            equalTo(SERVER_ADDRESS, cassandraHost),
                            equalTo(SERVER_PORT, cassandraPort),
                            equalTo(NETWORK_PEER_ADDRESS, cassandraIp),
                            equalTo(NETWORK_PEER_PORT, cassandraPort),
                            equalTo(maybeStable(DB_SYSTEM), CASSANDRA),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                emitStableDatabaseSemconv() ? stableQueryText : legacyQueryText),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv()
                                    ? "INSERT simple_values_test.users"
                                    : null),
                            equalTo(maybeStable(DB_OPERATION), "INSERT"),
                            equalTo(maybeStable(DB_CASSANDRA_CONSISTENCY_LEVEL), "LOCAL_ONE"),
                            equalTo(maybeStable(DB_CASSANDRA_COORDINATOR_DC), "datacenter1"),
                            satisfies(
                                maybeStable(DB_CASSANDRA_COORDINATOR_ID),
                                coordinatorIdAvailable
                                    ? val -> val.isInstanceOf(String.class)
                                    : val -> val.isNull()),
                            equalTo(maybeStable(DB_CASSANDRA_IDEMPOTENCE), false),
                            equalTo(maybeStable(DB_CASSANDRA_PAGE_SIZE), 5000),
                            satisfies(
                                maybeStable(DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT),
                                speculativeExecutionCountAvailable
                                    ? val -> val.isEqualTo(0)
                                    : val -> val.isNull()),
                            equalTo(maybeStable(DB_CASSANDRA_TABLE), "simple_values_test.users"))));
  }

  private static Stream<Arguments> simpleStatementScenarios() {
    return Stream.of(
        argumentSet(
            "no values",
            new SimpleStatement(
                "INSERT INTO simple_values_test.users (name, age) values ('carol', 3)"),
            "INSERT INTO simple_values_test.users (name, age) values (?, ?)",
            "INSERT INTO simple_values_test.users (name, age) values (?, ?)"),
        argumentSet(
            "positional values",
            new SimpleStatement(
                "INSERT INTO simple_values_test.users (name, age) values ('alice', ?)", 1),
            "INSERT INTO simple_values_test.users (name, age) values ('alice', ?)",
            "INSERT INTO simple_values_test.users (name, age) values (?, ?)"),
        argumentSet(
            "named values",
            new SimpleStatement(
                "INSERT INTO simple_values_test.users (name, age) values ('bob', :age)",
                ImmutableMap.<String, Object>of("age", 2)),
            "INSERT INTO simple_values_test.users (name, age) values ('bob', :age)",
            "INSERT INTO simple_values_test.users (name, age) values (?, :age)"));
  }

  @Test
  void testMetrics() {
    Session session = cluster.connect();
    cleanup.deferCleanup(session);

    session.execute("DROP KEYSPACE IF EXISTS metrics_test");
    session.execute(
        "CREATE KEYSPACE metrics_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':1}");
    testing.clearData();

    session.execute("CREATE TABLE metrics_test.users ( id UUID PRIMARY KEY, name text )");

    assertDurationMetric(
        testing,
        "io.opentelemetry.cassandra-3.0",
        DB_SYSTEM_NAME,
        DB_OPERATION_NAME,
        DB_COLLECTION_NAME,
        DB_QUERY_SUMMARY,
        NETWORK_PEER_ADDRESS,
        NETWORK_PEER_PORT,
        SERVER_ADDRESS,
        SERVER_PORT);
  }

  @ParameterizedTest
  @MethodSource("batchScenarios")
  void batchStatement(BatchScenario scenario) {
    Session session = cluster.connect();
    cleanup.deferCleanup(session);

    session.execute("DROP KEYSPACE IF EXISTS batch_test");
    session.execute(
        "CREATE KEYSPACE batch_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':1}");
    session.execute("CREATE TABLE batch_test.records ( id int PRIMARY KEY, num int )");
    testing.waitForTraces(3);
    testing.clearData();

    session.execute(scenario.buildBatch.apply(session));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv() ? scenario.spanName : scenario.oldSpanName)
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitStableDatabaseSemconv() ? null : "ipv4"),
                            equalTo(SERVER_ADDRESS, cassandraHost),
                            equalTo(SERVER_PORT, cassandraPort),
                            equalTo(NETWORK_PEER_ADDRESS, cassandraIp),
                            equalTo(NETWORK_PEER_PORT, cassandraPort),
                            equalTo(maybeStable(DB_SYSTEM), CASSANDRA),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                emitStableDatabaseSemconv()
                                    ? scenario.queryText
                                    : scenario.oldStatement),
                            equalTo(
                                DB_OPERATION_BATCH_SIZE,
                                emitStableDatabaseSemconv() ? scenario.batchSize : null),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? scenario.querySummary : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv()
                                    ? scenario.operationName
                                    : scenario.oldOperationName()),
                            equalTo(
                                maybeStable(DB_CASSANDRA_TABLE),
                                emitStableDatabaseSemconv()
                                    ? scenario.collectionName
                                    : scenario.oldCollectionName()),
                            equalTo(
                                maybeStable(DB_CASSANDRA_CONSISTENCY_LEVEL),
                                scenario.consistencyLevel),
                            equalTo(maybeStable(DB_CASSANDRA_COORDINATOR_DC), "datacenter1"),
                            satisfies(
                                maybeStable(DB_CASSANDRA_COORDINATOR_ID),
                                coordinatorIdAvailable
                                    ? val -> val.isInstanceOf(String.class)
                                    : val -> val.isNull()),
                            equalTo(maybeStable(DB_CASSANDRA_IDEMPOTENCE), scenario.idempotent),
                            equalTo(maybeStable(DB_CASSANDRA_PAGE_SIZE), scenario.pageSize),
                            satisfies(
                                maybeStable(DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT),
                                speculativeExecutionCountAvailable
                                    ? val -> val.isEqualTo(0)
                                    : val -> val.isNull()))));
  }

  private static boolean hasSpeculativeExecutionCount() {
    try {
      ExecutionInfo.class.getMethod("getSpeculativeExecutions");
      return true;
    } catch (NoSuchMethodException ignored) {
      return false;
    }
  }

  private static boolean hasCoordinatorId() {
    try {
      Host.class.getMethod("getHostId");
      return true;
    } catch (NoSuchMethodException ignored) {
      return false;
    }
  }

  @ParameterizedTest
  @MethodSource("failureScenarios")
  void failureTelemetry(Consumer<Session> execute) {
    Session session = cluster.connect();
    cleanup.deferCleanup(session);
    testing.clearData();

    assertThatThrownBy(() -> execute.accept(session)).isInstanceOf(SyntaxError.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? cassandraHost + ":" + cassandraPort
                                : "DB Query")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(EXCEPTION_TYPE, SyntaxError.class.getName()),
                                        satisfies(
                                            EXCEPTION_MESSAGE,
                                            val -> val.contains("line 1:0 no viable alternative")),
                                        satisfies(
                                            EXCEPTION_STACKTRACE,
                                            val -> val.isInstanceOf(String.class))))
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitStableDatabaseSemconv() ? null : "ipv4"),
                            equalTo(SERVER_ADDRESS, cassandraHost),
                            equalTo(SERVER_PORT, cassandraPort),
                            equalTo(NETWORK_PEER_ADDRESS, cassandraIp),
                            equalTo(NETWORK_PEER_PORT, cassandraPort),
                            equalTo(maybeStable(DB_SYSTEM), CASSANDRA),
                            equalTo(maybeStable(DB_STATEMENT), "invalid"),
                            equalTo(maybeStable(DB_CASSANDRA_CONSISTENCY_LEVEL), "LOCAL_ONE"),
                            equalTo(maybeStable(DB_CASSANDRA_IDEMPOTENCE), false),
                            equalTo(maybeStable(DB_CASSANDRA_PAGE_SIZE), 5000),
                            equalTo(
                                ERROR_TYPE,
                                emitStableDatabaseSemconv()
                                    ? SyntaxError.class.getName()
                                    : null))));
  }

  @Test
  void failureWithoutCoordinatorUsesConfiguredTargetOnlyForStableSemconv() {
    Cluster configuredCluster =
        Cluster.builder().addContactPoint("LOCALHOST").withPort(4242).build();
    cleanup.deferCleanup(configuredCluster);
    Session delegate = mock(Session.class);
    when(delegate.getCluster()).thenReturn(configuredCluster);
    RuntimeException failure = new RuntimeException("failed before execution");
    when(delegate.execute("invalid")).thenThrow(failure);
    Session session = new TracingSession(delegate);

    assertThatThrownBy(() -> session.execute("invalid")).isSameAs(failure);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "LOCALHOST:4242" : "DB Query")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(failure)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                SERVER_ADDRESS, emitStableDatabaseSemconv() ? "LOCALHOST" : null),
                            equalTo(SERVER_PORT, emitStableDatabaseSemconv() ? 4242L : null),
                            equalTo(maybeStable(DB_SYSTEM), CASSANDRA),
                            equalTo(maybeStable(DB_STATEMENT), "invalid"),
                            equalTo(maybeStable(DB_CASSANDRA_CONSISTENCY_LEVEL), "LOCAL_ONE"),
                            equalTo(maybeStable(DB_CASSANDRA_IDEMPOTENCE), false),
                            equalTo(maybeStable(DB_CASSANDRA_PAGE_SIZE), 5000),
                            equalTo(
                                ERROR_TYPE,
                                emitStableDatabaseSemconv()
                                    ? RuntimeException.class.getName()
                                    : null))));
  }

  private static Stream<Arguments> failureScenarios() {
    return Stream.of(
        argumentSet("sync", (Consumer<Session>) session -> session.execute("invalid")),
        argumentSet(
            "async",
            (Consumer<Session>) session -> session.executeAsync("invalid").getUninterruptibly()));
  }

  private static Stream<Arguments> batchScenarios() {
    return Stream.of(
        argumentSet(
            "empty",
            BatchScenario.builder()
                .buildBatch(session -> new BatchStatement())
                .spanName("BATCH")
                .oldSpanName("DB Query")
                .querySummary("BATCH")
                .batchSize(0)
                .idempotent(true)
                .build()),
        argumentSet(
            "single",
            BatchScenario.builder()
                .buildBatch(
                    session -> {
                      PreparedStatement insert =
                          session.prepare("INSERT INTO batch_test.records (id, num) values (?, ?)");
                      BatchStatement batch = new BatchStatement().add(insert.bind(1, 1));
                      batch.setConsistencyLevel(ConsistencyLevel.ONE);
                      batch.setFetchSize(123);
                      batch.setIdempotent(true);
                      return batch;
                    })
                .spanName("INSERT batch_test.records")
                .oldSpanName("INSERT batch_test.records")
                .queryText("INSERT INTO batch_test.records (id, num) values (?, ?)")
                .oldStatement("INSERT INTO batch_test.records (id, num) values (?, ?)")
                .querySummary("INSERT batch_test.records")
                .operationName("INSERT")
                .collectionName("batch_test.records")
                .consistencyLevel("ONE")
                .pageSize(123)
                .idempotent(true)
                .build()),
        argumentSet(
            "twoSameOperation",
            BatchScenario.builder()
                .buildBatch(
                    session -> {
                      PreparedStatement insert =
                          session.prepare("INSERT INTO batch_test.records (id, num) values (?, ?)");
                      return new BatchStatement().add(insert.bind(1, 1)).add(insert.bind(2, 2));
                    })
                .spanName("BATCH INSERT batch_test.records")
                .oldSpanName("DB Query")
                .queryText("INSERT INTO batch_test.records (id, num) values (?, ?)")
                .querySummary("BATCH INSERT batch_test.records")
                .batchSize(2)
                .operationName("BATCH INSERT")
                .collectionName("batch_test.records")
                .build()),
        argumentSet(
            "twoDifferentOperations",
            BatchScenario.builder()
                .buildBatch(
                    session -> {
                      return new BatchStatement()
                          .add(
                              new SimpleStatement(
                                  "INSERT INTO batch_test.records (id, num) values (4, ?)", 4))
                          .add(
                              new SimpleStatement(
                                  "UPDATE batch_test.records SET num = 5 WHERE id = 4"));
                    })
                .spanName("BATCH")
                .oldSpanName("DB Query")
                .queryText(
                    "INSERT INTO batch_test.records (id, num) values (4, ?); UPDATE batch_test.records SET num = ? WHERE id = ?")
                .querySummary("BATCH")
                .batchSize(2)
                .operationName("BATCH")
                .collectionName("batch_test.records")
                .build()));
  }

  private static Stream<Arguments> provideSyncParameters() {
    return Stream.of(
        Arguments.of(
            named(
                "Drop keyspace if exists",
                new Parameter(
                    null,
                    "DROP KEYSPACE IF EXISTS sync_test",
                    "DROP KEYSPACE IF EXISTS sync_test",
                    emitStableDatabaseSemconv() ? "DROP KEYSPACE" : "DROP",
                    "DROP",
                    null))),
        Arguments.of(
            named(
                "Create keyspace with replication",
                new Parameter(
                    null,
                    "CREATE KEYSPACE sync_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':3}",
                    "CREATE KEYSPACE sync_test WITH REPLICATION = {?:?, ?:?}",
                    emitStableDatabaseSemconv() ? "CREATE KEYSPACE" : "CREATE",
                    "CREATE",
                    null))),
        Arguments.of(
            named(
                "Create table",
                new Parameter(
                    "sync_test",
                    "CREATE TABLE sync_test.users ( id UUID PRIMARY KEY, name text )",
                    "CREATE TABLE sync_test.users ( id UUID PRIMARY KEY, name text )",
                    "CREATE TABLE sync_test.users",
                    "CREATE TABLE",
                    "sync_test.users"))),
        Arguments.of(
            named(
                "Insert data",
                new Parameter(
                    "sync_test",
                    "INSERT INTO sync_test.users (id, name) values (uuid(), 'alice')",
                    "INSERT INTO sync_test.users (id, name) values (uuid(), ?)",
                    "INSERT sync_test.users",
                    "INSERT",
                    "sync_test.users"))),
        Arguments.of(
            named(
                "Select data",
                new Parameter(
                    "sync_test",
                    "SELECT * FROM users where name = 'alice' ALLOW FILTERING",
                    "SELECT * FROM users where name = ? ALLOW FILTERING",
                    emitStableDatabaseSemconv() ? "SELECT users" : "SELECT sync_test.users",
                    "SELECT",
                    "users"))));
  }

  private static Stream<Arguments> provideAsyncParameters() {
    return Stream.of(
        Arguments.of(
            named(
                "Drop keyspace if exists",
                new Parameter(
                    null,
                    "DROP KEYSPACE IF EXISTS async_test",
                    "DROP KEYSPACE IF EXISTS async_test",
                    emitStableDatabaseSemconv() ? "DROP KEYSPACE" : "DROP",
                    "DROP",
                    null))),
        Arguments.of(
            named(
                "Create keyspace with replication",
                new Parameter(
                    null,
                    "CREATE KEYSPACE async_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':3}",
                    "CREATE KEYSPACE async_test WITH REPLICATION = {?:?, ?:?}",
                    emitStableDatabaseSemconv() ? "CREATE KEYSPACE" : "CREATE",
                    "CREATE",
                    null))),
        Arguments.of(
            named(
                "Create table",
                new Parameter(
                    "async_test",
                    "CREATE TABLE async_test.users ( id UUID PRIMARY KEY, name text )",
                    "CREATE TABLE async_test.users ( id UUID PRIMARY KEY, name text )",
                    "CREATE TABLE async_test.users",
                    "CREATE TABLE",
                    "async_test.users"))),
        Arguments.of(
            named(
                "Insert data",
                new Parameter(
                    "async_test",
                    "INSERT INTO async_test.users (id, name) values (uuid(), 'alice')",
                    "INSERT INTO async_test.users (id, name) values (uuid(), ?)",
                    "INSERT async_test.users",
                    "INSERT",
                    "async_test.users"))),
        Arguments.of(
            named(
                "Select data",
                new Parameter(
                    "async_test",
                    "SELECT * FROM users where name = 'alice' ALLOW FILTERING",
                    "SELECT * FROM users where name = ? ALLOW FILTERING",
                    emitStableDatabaseSemconv() ? "SELECT users" : "SELECT async_test.users",
                    "SELECT",
                    "users"))));
  }

  private static class Parameter {
    final String keyspace;
    final String queryText;
    final String expectedQueryText;
    final String spanName;
    final String operation;
    final String table;

    Parameter(
        String keyspace,
        String queryText,
        String expectedQueryText,
        String spanName,
        String operation,
        String table) {
      this.keyspace = keyspace;
      this.queryText = queryText;
      this.expectedQueryText = expectedQueryText;
      this.spanName = spanName;
      this.operation = operation;
      this.table = table;
    }
  }

  private static class BatchScenario {
    final Function<Session, BatchStatement> buildBatch;
    final String spanName;
    final String oldSpanName;
    final String queryText;
    final String oldStatement;
    final String querySummary;
    final Long batchSize;
    final String operationName;
    final String collectionName;
    final String consistencyLevel;
    final int pageSize;
    final boolean idempotent;

    BatchScenario(Builder builder) {
      this.buildBatch = builder.buildBatch;
      this.spanName = builder.spanName;
      this.oldSpanName = builder.oldSpanName;
      this.queryText = builder.queryText;
      this.oldStatement = builder.oldStatement;
      this.querySummary = builder.querySummary;
      this.batchSize = builder.batchSize;
      this.operationName = builder.operationName;
      this.collectionName = builder.collectionName;
      this.consistencyLevel = builder.consistencyLevel;
      this.pageSize = builder.pageSize;
      this.idempotent = builder.idempotent;
    }

    static Builder builder() {
      return new Builder();
    }

    String oldOperationName() {
      return batchSize == null ? operationName : null;
    }

    String oldCollectionName() {
      return batchSize == null ? collectionName : null;
    }

    static class Builder {
      private Function<Session, BatchStatement> buildBatch;
      private String spanName;
      private String oldSpanName;
      private String queryText;
      private String oldStatement;
      private String querySummary;
      private Long batchSize;
      private String operationName;
      private String collectionName;
      private String consistencyLevel = "LOCAL_ONE";
      private int pageSize = 5000;
      private boolean idempotent;

      Builder buildBatch(Function<Session, BatchStatement> buildBatch) {
        this.buildBatch = buildBatch;
        return this;
      }

      Builder spanName(String spanName) {
        this.spanName = spanName;
        return this;
      }

      Builder oldSpanName(String oldSpanName) {
        this.oldSpanName = oldSpanName;
        return this;
      }

      Builder queryText(String queryText) {
        this.queryText = queryText;
        return this;
      }

      Builder oldStatement(String oldStatement) {
        this.oldStatement = oldStatement;
        return this;
      }

      Builder querySummary(String querySummary) {
        this.querySummary = querySummary;
        return this;
      }

      Builder batchSize(long batchSize) {
        this.batchSize = batchSize;
        return this;
      }

      Builder operationName(String operationName) {
        this.operationName = operationName;
        return this;
      }

      Builder collectionName(String collectionName) {
        this.collectionName = collectionName;
        return this;
      }

      Builder consistencyLevel(String consistencyLevel) {
        this.consistencyLevel = consistencyLevel;
        return this;
      }

      Builder pageSize(int pageSize) {
        this.pageSize = pageSize;
        return this;
      }

      Builder idempotent(boolean idempotent) {
        this.idempotent = idempotent;
        return this;
      }

      BatchScenario build() {
        return new BatchScenario(this);
      }
    }
  }
}
