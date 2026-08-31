/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.comparingRootSpanAttribute;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SQL_TABLE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_USER;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.POSTGRESQL;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.counting;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgException;
import io.vertx.sqlclient.ClientBuilder;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.PreparedQuery;
import io.vertx.sqlclient.PreparedStatement;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.SqlConnectOptions;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
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
class VertxSqlClientTest {
  private static final Logger logger = LoggerFactory.getLogger(VertxSqlClientTest.class);

  private static final String USER_DB = "SA";
  private static final String PW_DB = "password123";
  private static final String DB = "tempdb";

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static GenericContainer<?> container;
  private static Vertx vertx;
  private static Pool pool;
  private static String host;
  private static int port;

  @BeforeAll
  static void setUp() throws Exception {
    container =
        new GenericContainer<>("postgres:9.6.8")
            .withEnv("POSTGRES_USER", USER_DB)
            .withEnv("POSTGRES_PASSWORD", PW_DB)
            .withEnv("POSTGRES_DB", DB)
            .withExposedPorts(5432)
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .withStartupTimeout(Duration.ofMinutes(2));
    container.start();
    cleanup.deferAfterAll(container::stop);
    vertx = Vertx.vertx();
    cleanup.deferAfterAll(vertx::close);
    host = container.getHost();
    port = container.getMappedPort(5432);
    PgConnectOptions options =
        new PgConnectOptions()
            .setPort(port)
            .setHost(host)
            .setDatabase(DB)
            .setUser(USER_DB)
            .setPassword(PW_DB);
    pool = Pool.pool(vertx, options, new PoolOptions().setMaxSize(4));
    cleanup.deferAfterAll(pool::close);
    pool.query("create table test(id int primary key, name varchar(255))")
        .execute()
        .compose(
            r ->
                // insert some test data
                pool.query("insert into test values (1, 'Hello'), (2, 'World')").execute())
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, SECONDS);
  }

  @Test
  void testConnectingToServerListReportsTheWholeConfiguredTarget() throws Exception {
    PgConnectOptions first = connectOptions();
    PgConnectOptions second = new PgConnectOptions(first).setPort(port + 1);
    Pool listPool =
        PgBuilder.pool()
            .using(vertx)
            .connectingTo(asList(first, second))
            .with(new PoolOptions().setMaxSize(1))
            .build();
    cleanup.deferCleanup(listPool::close);

    select(listPool);

    testing.waitAndAssertTraces(trace -> assertServerGroup(trace, port + 1));
  }

  @Test
  void testConnectHandlerReportsTheWholeConfiguredTarget() throws Exception {
    PgConnectOptions first = connectOptions();
    PgConnectOptions second = new PgConnectOptions(first).setPort(port + 1);
    CompletableFuture<Void> handlerInvoked = new CompletableFuture<>();
    Pool listPool =
        PgBuilder.pool()
            .using(vertx)
            .connectingTo(asList(first, second))
            .withConnectHandler(
                connection -> {
                  connection
                      .query("select * from test")
                      .execute()
                      .onComplete(ignored -> connection.close());
                  handlerInvoked.complete(null);
                })
            .with(new PoolOptions().setMaxSize(1))
            .build();
    cleanup.deferCleanup(listPool::close);

    SqlConnection connection =
        listPool.getConnection().toCompletionStage().toCompletableFuture().get(30, SECONDS);
    cleanup.deferCleanup(connection::close);
    handlerInvoked.get(30, SECONDS);

    testing.waitAndAssertTraces(trace -> assertServerGroup(trace, port + 1));
  }

  @Test
  void testConnectingToSupplierCapturesTheSuppliedOptions() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    PgConnectOptions suppliedOptions = connectOptions();
    Pool supplierPool =
        PgBuilder.pool()
            .using(vertx)
            .connectingTo(
                () -> {
                  calls.incrementAndGet();
                  return Future.succeededFuture(suppliedOptions);
                })
            .with(new PoolOptions().setMaxSize(1))
            .build();
    cleanup.deferCleanup(supplierPool::close);

    select(supplierPool);

    assertThat(calls).hasValue(1);
    testing.waitAndAssertTraces(VertxSqlClientTest::assertSupplierTarget);
  }

  @Test
  void testConnectingToGenericSupplierUsesDriverDbSystem() throws Exception {
    SqlConnectOptions suppliedOptions = new PgConnectOptions(connectOptions()) {};
    Pool supplierPool =
        PgBuilder.pool()
            .using(vertx)
            .connectingTo(() -> Future.succeededFuture(suppliedOptions))
            .with(new PoolOptions().setMaxSize(1))
            .build();
    cleanup.deferCleanup(supplierPool::close);

    select(supplierPool);

    testing.waitAndAssertTraces(VertxSqlClientTest::assertSupplierTarget);
  }

  @Test
  void testQueuedQueriesCaptureTheSuppliedOptions() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    Promise<SqlConnectOptions> suppliedOptions = Promise.promise();
    Pool supplierPool =
        PgBuilder.pool()
            .using(vertx)
            .connectingTo(
                () -> {
                  calls.incrementAndGet();
                  return suppliedOptions.future();
                })
            .with(new PoolOptions().setMaxSize(1))
            .build();
    cleanup.deferCleanup(supplierPool::close);

    Future<?> firstResult = supplierPool.query("select * from test").execute();
    Future<?> secondResult = supplierPool.query("select * from test").execute();
    suppliedOptions.complete(connectOptions());
    Future.all(firstResult, secondResult)
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, SECONDS);

    assertThat(calls).hasValue(1);
    testing.waitAndAssertTraces(
        VertxSqlClientTest::assertSupplierTarget, VertxSqlClientTest::assertSupplierTarget);
  }

  @Test
  void testSupplierCaptureTracksChangingOptionsWithoutRetainingThem() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    PgConnectOptions first = connectOptions();
    String alternateHost = host.equals("localhost") ? "127.0.0.1" : "localhost";
    PgConnectOptions second = new PgConnectOptions(first).setHost(alternateHost);
    Pool supplierPool =
        PgBuilder.pool()
            .using(vertx)
            .connectingTo(
                () -> Future.succeededFuture(calls.getAndIncrement() == 0 ? first : second))
            .with(new PoolOptions().setMaxSize(2))
            .build();
    cleanup.deferCleanup(supplierPool::close);

    SqlConnection firstConnection =
        supplierPool.getConnection().toCompletionStage().toCompletableFuture().get(30, SECONDS);
    cleanup.deferCleanup(firstConnection::close);
    first.setHost("mutated.example");
    select(firstConnection);

    SqlConnection secondConnection =
        supplierPool.getConnection().toCompletionStage().toCompletableFuture().get(30, SECONDS);
    cleanup.deferCleanup(secondConnection::close);
    select(secondConnection);
    select(firstConnection);

    assertThat(calls).hasValue(2);
    testing.waitAndAssertTraces(
        trace -> assertSupplierTarget(trace, host),
        trace -> assertSupplierTarget(trace, alternateHost),
        trace -> assertSupplierTarget(trace, host));
  }

  @Test
  void testConcurrentSupplierConnectionsKeepTheirOptions() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    Promise<SqlConnectOptions> firstOptions = Promise.promise();
    Promise<SqlConnectOptions> secondOptions = Promise.promise();
    PgConnectOptions first = connectOptions();
    String alternateHost = host.equals("localhost") ? "127.0.0.1" : "localhost";
    PgConnectOptions second = new PgConnectOptions(first).setHost(alternateHost);
    Pool supplierPool =
        PgBuilder.pool()
            .using(vertx)
            .connectingTo(
                () -> calls.getAndIncrement() == 0 ? firstOptions.future() : secondOptions.future())
            .with(new PoolOptions().setMaxSize(2))
            .build();
    cleanup.deferCleanup(supplierPool::close);

    Future<SqlConnection> firstConnectionFuture = supplierPool.getConnection();
    Future<SqlConnection> secondConnectionFuture = supplierPool.getConnection();
    firstOptions.complete(first);
    secondOptions.complete(second);
    SqlConnection firstConnection =
        firstConnectionFuture.toCompletionStage().toCompletableFuture().get(30, SECONDS);
    cleanup.deferCleanup(firstConnection::close);
    SqlConnection secondConnection =
        secondConnectionFuture.toCompletionStage().toCompletableFuture().get(30, SECONDS);
    cleanup.deferCleanup(secondConnection::close);

    select(firstConnection);
    select(secondConnection);

    assertThat(calls).hasValue(2);
    testing.waitAndAssertTraces(
        trace -> assertSupplierTarget(trace, host),
        trace -> assertSupplierTarget(trace, alternateHost));
  }

  @Test
  void testConcurrentSupplierPoolQueriesKeepTheirOptions() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    Promise<SqlConnectOptions> firstOptions = Promise.promise();
    Promise<SqlConnectOptions> secondOptions = Promise.promise();
    PgConnectOptions first = connectOptions();
    String alternateHost = host.equals("localhost") ? "127.0.0.1" : "localhost";
    PgConnectOptions second = new PgConnectOptions(first).setHost(alternateHost);
    Pool supplierPool =
        PgBuilder.pool()
            .using(vertx)
            .connectingTo(
                () -> calls.getAndIncrement() == 0 ? firstOptions.future() : secondOptions.future())
            .with(new PoolOptions().setMaxSize(2))
            .build();
    cleanup.deferCleanup(supplierPool::close);

    Future<?> firstResult = supplierPool.query("select * from test").execute();
    Future<?> secondResult = supplierPool.query("select * from test").execute();
    firstOptions.complete(first);
    secondOptions.complete(second);
    Future.all(firstResult, secondResult)
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, SECONDS);

    assertThat(calls).hasValue(2);
    List<String> expectedHosts = asList(host, alternateHost);
    Collections.sort(expectedHosts);
    testing.waitAndAssertSortedTraces(
        comparingRootSpanAttribute(SERVER_ADDRESS),
        trace -> assertSupplierTarget(trace, expectedHosts.get(0)),
        trace -> assertSupplierTarget(trace, expectedHosts.get(1)));
  }

  @Test
  void testSupplierCapturePreservesExceptions() {
    RuntimeException thrown = new RuntimeException("supplier failed");
    Pool throwingPool =
        PgBuilder.pool()
            .using(vertx)
            .connectingTo(
                () -> {
                  throw thrown;
                })
            .with(new PoolOptions().setMaxSize(1))
            .build();
    cleanup.deferCleanup(throwingPool::close);

    assertThatThrownBy(() -> select(throwingPool)).isSameAs(thrown);

    RuntimeException failed = new RuntimeException("future failed");
    Pool failingPool =
        PgBuilder.pool()
            .using(vertx)
            .connectingTo(() -> Future.failedFuture(failed))
            .with(new PoolOptions().setMaxSize(1))
            .build();
    cleanup.deferCleanup(failingPool::close);

    assertThatThrownBy(() -> select(failingPool)).hasCause(failed);

    testing.waitAndAssertTraces(
        trace -> assertSupplierFailure(trace, thrown),
        trace -> assertSupplierFailure(trace, failed));
  }

  @Test
  void testConnectingToDirectOptionsCapturesTarget() throws Exception {
    PgConnectOptions options = connectOptions();
    Pool directPool =
        PgBuilder.pool()
            .using(vertx)
            .connectingTo(options)
            .with(new PoolOptions().setMaxSize(1))
            .build();
    cleanup.deferCleanup(directPool::close);

    select(directPool);

    testing.waitAndAssertTraces(VertxSqlClientTest::assertDirectTarget);
  }

  @Test
  void testExplicitPreparedStatementWithServerListReportsTheWholeConfiguredTarget()
      throws Exception {
    PgConnectOptions first = connectOptions();
    PgConnectOptions second = new PgConnectOptions(first).setPort(port + 1);
    Pool listPool =
        PgBuilder.pool()
            .using(vertx)
            .connectingTo(asList(first, second))
            .with(new PoolOptions().setMaxSize(1))
            .build();
    cleanup.deferCleanup(listPool::close);
    String query = "select * from test where id = $1";

    executePreparedStatement(listPool, query, Tuple.of(1))
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, SECONDS);

    testing.waitAndAssertTraces(trace -> assertServerGroup(trace, port + 1, query));
  }

  @Test
  void testOneBuilderGivesEachClientItsOwnTarget() throws Exception {
    PgConnectOptions first = connectOptions();
    ClientBuilder<Pool> builder =
        PgBuilder.pool().using(vertx).with(new PoolOptions().setMaxSize(1));

    Pool firstPool =
        builder.connectingTo(asList(first, new PgConnectOptions(first).setPort(port + 1))).build();
    cleanup.deferCleanup(firstPool::close);
    Pool secondPool =
        builder.connectingTo(asList(first, new PgConnectOptions(first).setPort(port + 2))).build();
    cleanup.deferCleanup(secondPool::close);

    select(firstPool);
    select(secondPool);

    testing.waitAndAssertTraces(
        trace -> assertServerGroup(trace, port + 1), trace -> assertServerGroup(trace, port + 2));
  }

  @Test
  void testMutableServerListIsSnapshottedForEachBuild() throws Exception {
    PgConnectOptions first = connectOptions();
    String alternateHost = host.equals("localhost") ? "127.0.0.1" : "localhost";
    List<SqlConnectOptions> databases =
        new ArrayList<>(asList(first, new PgConnectOptions(first).setHost(alternateHost)));
    ClientBuilder<Pool> builder =
        PgBuilder.pool().using(vertx).connectingTo(databases).with(new PoolOptions().setMaxSize(1));

    Pool firstPool = builder.build();
    cleanup.deferCleanup(firstPool::close);
    databases.set(1, new PgConnectOptions(first));
    Pool secondPool = builder.build();
    cleanup.deferCleanup(secondPool::close);

    select(firstPool);
    select(secondPool);

    testing.waitAndAssertTraces(
        trace -> assertServerGroup(trace, alternateHost, port),
        trace -> assertServerGroup(trace, host, port));
  }

  @Test
  void testSwitchingTheBuilderToOneServerDropsTheServerList() throws Exception {
    PgConnectOptions first = connectOptions();
    ClientBuilder<Pool> builder =
        PgBuilder.pool().using(vertx).with(new PoolOptions().setMaxSize(1));
    builder.connectingTo(asList(first, new PgConnectOptions(first).setPort(port + 1)));

    Pool singlePool = builder.connectingTo(first).build();
    cleanup.deferCleanup(singlePool::close);

    select(singlePool);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                maybeStable(DB_SYSTEM),
                                emitStableDatabaseSemconv() ? POSTGRESQL : null),
                            equalTo(maybeStable(DB_NAME), DB),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : USER_DB),
                            equalTo(maybeStable(DB_STATEMENT), "select * from test"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "select test" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"),
                            equalTo(
                                maybeStable(DB_SQL_TABLE),
                                emitStableDatabaseSemconv() ? null : "test"),
                            equalTo(maybeStablePeerService(), "test-peer-service"),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, Long.valueOf(port)))));
  }

  private static void assertServerGroup(TraceAssert trace, int secondPort) {
    assertServerGroup(trace, host, secondPort, "select * from test");
  }

  private static void assertServerGroup(TraceAssert trace, int secondPort, String statement) {
    assertServerGroup(trace, host, secondPort, statement);
  }

  private static void assertServerGroup(TraceAssert trace, String secondHost, int secondPort) {
    assertServerGroup(trace, secondHost, secondPort, "select * from test");
  }

  private static void assertServerGroup(
      TraceAssert trace, String secondHost, int secondPort, String statement) {
    List<String> addresses = asList(host + ":" + port, secondHost + ":" + secondPort);
    Collections.sort(addresses);
    trace.hasSpansSatisfyingExactly(
        span ->
            span.hasKind(SpanKind.CLIENT)
                .hasAttributesSatisfyingExactly(
                    equalTo(
                        maybeStable(DB_SYSTEM), emitStableDatabaseSemconv() ? POSTGRESQL : null),
                    equalTo(maybeStable(DB_NAME), DB),
                    equalTo(DB_USER, emitStableDatabaseSemconv() ? null : USER_DB),
                    equalTo(maybeStable(DB_STATEMENT), statement),
                    equalTo(DB_QUERY_SUMMARY, emitStableDatabaseSemconv() ? "select test" : null),
                    equalTo(
                        maybeStable(DB_OPERATION), emitStableDatabaseSemconv() ? null : "SELECT"),
                    equalTo(maybeStable(DB_SQL_TABLE), emitStableDatabaseSemconv() ? null : "test"),
                    equalTo(
                        maybeStablePeerService(),
                        emitStableDatabaseSemconv() ? null : "test-peer-service"),
                    equalTo(
                        SERVER_ADDRESS,
                        emitStableDatabaseSemconv() ? String.join(",", addresses) : host),
                    equalTo(SERVER_PORT, emitStableDatabaseSemconv() ? null : Long.valueOf(port))));
  }

  private static void assertDirectTarget(TraceAssert trace) {
    trace.hasSpansSatisfyingExactly(
        span ->
            span.hasKind(SpanKind.CLIENT)
                .hasAttributesSatisfyingExactly(
                    equalTo(
                        maybeStable(DB_SYSTEM), emitStableDatabaseSemconv() ? POSTGRESQL : null),
                    equalTo(maybeStable(DB_NAME), DB),
                    equalTo(DB_USER, emitStableDatabaseSemconv() ? null : USER_DB),
                    equalTo(maybeStable(DB_STATEMENT), "select * from test"),
                    equalTo(DB_QUERY_SUMMARY, emitStableDatabaseSemconv() ? "select test" : null),
                    equalTo(
                        maybeStable(DB_OPERATION), emitStableDatabaseSemconv() ? null : "SELECT"),
                    equalTo(maybeStable(DB_SQL_TABLE), emitStableDatabaseSemconv() ? null : "test"),
                    equalTo(maybeStablePeerService(), "test-peer-service"),
                    equalTo(SERVER_ADDRESS, host),
                    equalTo(SERVER_PORT, Long.valueOf(port))));
  }

  private static void assertSupplierTarget(TraceAssert trace) {
    assertSupplierTarget(trace, host);
  }

  private static void assertSupplierTarget(TraceAssert trace, String expectedHost) {
    if (emitOldDatabaseSemconv() && emitStableDatabaseSemconv()) {
      trace.hasSpansSatisfyingExactly(
          span ->
              span.hasName("select test")
                  .hasKind(SpanKind.CLIENT)
                  .hasAttributesSatisfyingExactly(
                      equalTo(maybeStable(DB_SYSTEM), POSTGRESQL),
                      equalTo(maybeStable(DB_NAME), DB),
                      equalTo(maybeStable(DB_STATEMENT), "select * from test"),
                      equalTo(DB_QUERY_SUMMARY, "select test"),
                      equalTo(DB_NAME, DB),
                      equalTo(DB_USER, USER_DB),
                      equalTo(DB_STATEMENT, "select * from test"),
                      equalTo(DB_OPERATION, "SELECT"),
                      equalTo(DB_SQL_TABLE, "test"),
                      equalTo(maybeStablePeerService(), "test-peer-service"),
                      equalTo(SERVER_ADDRESS, expectedHost),
                      equalTo(SERVER_PORT, Long.valueOf(port))));
      return;
    }
    trace.hasSpansSatisfyingExactly(
        span ->
            span.hasName(emitStableDatabaseSemconv() ? "select test" : "SELECT tempdb.test")
                .hasKind(SpanKind.CLIENT)
                .hasAttributesSatisfyingExactly(
                    equalTo(
                        maybeStable(DB_SYSTEM), emitStableDatabaseSemconv() ? POSTGRESQL : null),
                    equalTo(maybeStable(DB_NAME), DB),
                    equalTo(DB_USER, emitStableDatabaseSemconv() ? null : USER_DB),
                    equalTo(maybeStable(DB_STATEMENT), "select * from test"),
                    equalTo(DB_QUERY_SUMMARY, emitStableDatabaseSemconv() ? "select test" : null),
                    equalTo(
                        maybeStable(DB_OPERATION), emitStableDatabaseSemconv() ? null : "SELECT"),
                    equalTo(maybeStable(DB_SQL_TABLE), emitStableDatabaseSemconv() ? null : "test"),
                    equalTo(maybeStablePeerService(), "test-peer-service"),
                    equalTo(SERVER_ADDRESS, expectedHost),
                    equalTo(SERVER_PORT, Long.valueOf(port))));
  }

  private static void assertSupplierFailure(TraceAssert trace, RuntimeException error) {
    trace.hasSpansSatisfyingExactly(
        span ->
            span.hasName(emitStableDatabaseSemconv() ? "select test" : "SELECT test")
                .hasKind(SpanKind.CLIENT)
                .hasStatus(StatusData.error())
                .hasEventsSatisfyingExactly(
                    event ->
                        event
                            .hasName("exception")
                            .hasAttributesSatisfyingExactly(
                                equalTo(EXCEPTION_TYPE, error.getClass().getName()),
                                equalTo(EXCEPTION_MESSAGE, error.getMessage()),
                                satisfies(
                                    EXCEPTION_STACKTRACE, val -> val.isInstanceOf(String.class))))
                .hasAttributesSatisfyingExactly(
                    equalTo(
                        maybeStable(DB_SYSTEM), emitStableDatabaseSemconv() ? POSTGRESQL : null),
                    equalTo(maybeStable(DB_STATEMENT), "select * from test"),
                    equalTo(DB_QUERY_SUMMARY, emitStableDatabaseSemconv() ? "select test" : null),
                    equalTo(
                        maybeStable(DB_OPERATION), emitStableDatabaseSemconv() ? null : "SELECT"),
                    equalTo(maybeStable(DB_SQL_TABLE), emitStableDatabaseSemconv() ? null : "test"),
                    equalTo(
                        ERROR_TYPE,
                        emitStableDatabaseSemconv() ? error.getClass().getName() : null)));
  }

  private static void select(SqlClient client) throws Exception {
    client
        .query("select * from test")
        .execute()
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, SECONDS);
  }

  private static PgConnectOptions connectOptions() {
    return new PgConnectOptions()
        .setPort(port)
        .setHost(host)
        .setDatabase(DB)
        .setUser(USER_DB)
        .setPassword(PW_DB);
  }

  @Test
  void testSimpleSelect() throws Exception {
    CompletableFuture<Object> future = new CompletableFuture<>();
    CompletableFuture<Object> result =
        future.whenComplete((rows, throwable) -> testing.runWithSpan("callback", () -> {}));
    testing.runWithSpan(
        "parent",
        () ->
            pool.query("select * from test")
                .execute()
                .onComplete(
                    rowSetAsyncResult -> {
                      if (rowSetAsyncResult.succeeded()) {
                        future.complete(rowSetAsyncResult.result());
                      } else {
                        future.completeExceptionally(rowSetAsyncResult.cause());
                      }
                    }));
    result.get(30, SECONDS);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "select test" : "SELECT tempdb.test")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                maybeStable(DB_SYSTEM),
                                emitStableDatabaseSemconv() ? POSTGRESQL : null),
                            equalTo(maybeStable(DB_NAME), DB),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : USER_DB),
                            equalTo(maybeStable(DB_STATEMENT), "select * from test"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "select test" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"),
                            equalTo(
                                maybeStable(DB_SQL_TABLE),
                                emitStableDatabaseSemconv() ? null : "test"),
                            equalTo(maybeStablePeerService(), "test-peer-service"),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port)),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));

    assertDurationMetric(
        testing,
        "io.opentelemetry.vertx-sql-client-5.0",
        DB_SYSTEM_NAME,
        DB_NAMESPACE,
        DB_QUERY_SUMMARY,
        SERVER_ADDRESS,
        SERVER_PORT);
  }

  @Test
  void testInvalidQuery() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    CompletableFuture<Object> result = new CompletableFuture<>();
    result.whenComplete((rows, throwable) -> testing.runWithSpan("callback", latch::countDown));
    testing.runWithSpan(
        "parent",
        () ->
            pool.query("invalid")
                .execute()
                .onComplete(
                    rowSetAsyncResult -> {
                      if (rowSetAsyncResult.succeeded()) {
                        result.complete(rowSetAsyncResult.result());
                      } else {
                        result.completeExceptionally(rowSetAsyncResult.cause());
                      }
                    }));

    assertThat(latch.await(30, SECONDS)).isTrue();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                span ->
                    span.hasName("tempdb")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(EXCEPTION_TYPE, PgException.class.getName()),
                                        satisfies(
                                            EXCEPTION_MESSAGE,
                                            val -> val.contains("syntax error at or near")),
                                        satisfies(
                                            EXCEPTION_STACKTRACE,
                                            val -> val.isInstanceOf(String.class))))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                maybeStable(DB_SYSTEM),
                                emitStableDatabaseSemconv() ? POSTGRESQL : null),
                            equalTo(maybeStable(DB_NAME), DB),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : USER_DB),
                            equalTo(maybeStable(DB_STATEMENT), "invalid"),
                            equalTo(maybeStablePeerService(), "test-peer-service"),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(ERROR_TYPE, emitStableDatabaseSemconv() ? "42601" : null)),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void testPreparedSelect() throws Exception {
    String query = "select * from test where id = $1 and name = 'Hello'";
    testing
        .runWithSpan("parent", () -> pool.preparedQuery(query).execute(Tuple.of(1)))
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, SECONDS);

    assertPreparedSelect(query, "select * from test where id = $1 and name = ?");
  }

  private static void assertPreparedSelect() {
    String query = "select * from test where id = $1";
    assertPreparedSelect(query, query);
  }

  private static void assertPreparedSelect(String query, String sanitizedQuery) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "select test" : "SELECT tempdb.test")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                maybeStable(DB_SYSTEM),
                                emitStableDatabaseSemconv() ? POSTGRESQL : null),
                            equalTo(maybeStable(DB_NAME), DB),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : USER_DB),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                emitStableDatabaseSemconv() ? query : sanitizedQuery),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "select test" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"),
                            equalTo(
                                maybeStable(DB_SQL_TABLE),
                                emitStableDatabaseSemconv() ? null : "test"),
                            equalTo(maybeStablePeerService(), "test-peer-service"),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port))));
  }

  @Test
  void testExplicitPreparedSelect() throws Exception {
    String query = "select * from test where id = $1 and name = 'Hello'";
    testing
        .runWithSpan("parent", () -> executePreparedStatement(query, Tuple.of(1)))
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, SECONDS);

    assertPreparedSelect(query, "select * from test where id = $1 and name = ?");

    assertDurationMetric(
        testing,
        "io.opentelemetry.vertx-sql-client-5.0",
        DB_SYSTEM_NAME,
        DB_NAMESPACE,
        DB_QUERY_SUMMARY,
        SERVER_ADDRESS,
        SERVER_PORT);
  }

  @Test
  void testMappedExplicitPreparedSelect() throws Exception {
    String query = "select * from test where id = $1";
    testing
        .runWithSpan(
            "parent",
            () ->
                executePreparedStatement(
                    query,
                    Tuple.of(1),
                    statement -> statement.query().mapping(row -> row.getInteger("id"))))
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, SECONDS);

    assertPreparedSelect();
  }

  @Test
  void testCollectedExplicitPreparedSelect() throws Exception {
    String query = "select * from test where id = $1";
    testing
        .runWithSpan(
            "parent",
            () ->
                executePreparedStatement(
                    query, Tuple.of(1), statement -> statement.query().collecting(counting())))
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, SECONDS);

    assertPreparedSelect();
  }

  @Test
  void testExplicitPreparedSelectFailure() throws Exception {
    String query = "select * from test where id = $1 or $1 / $1 = 0";
    try {
      testing
          .runWithSpan("parent", () -> executePreparedStatement(query, Tuple.of(0)))
          .toCompletionStage()
          .toCompletableFuture()
          .get(30, SECONDS);
    } catch (ExecutionException ignored) {
      // the failure is recorded on the client span
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "select test" : "SELECT tempdb.test")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(EXCEPTION_TYPE, PgException.class.getName()),
                                        satisfies(
                                            EXCEPTION_MESSAGE,
                                            val -> val.contains("division by zero")),
                                        satisfies(
                                            EXCEPTION_STACKTRACE,
                                            val -> val.isInstanceOf(String.class))))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                maybeStable(DB_SYSTEM),
                                emitStableDatabaseSemconv() ? POSTGRESQL : null),
                            equalTo(maybeStable(DB_NAME), DB),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : USER_DB),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                emitStableDatabaseSemconv()
                                    ? query
                                    : "select * from test where id = $1 or $1 / $1 = ?"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "select test" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"),
                            equalTo(
                                maybeStable(DB_SQL_TABLE),
                                emitStableDatabaseSemconv() ? null : "test"),
                            equalTo(maybeStablePeerService(), "test-peer-service"),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(ERROR_TYPE, emitStableDatabaseSemconv() ? "22012" : null))));
  }

  private static Future<?> executePreparedStatement(String query, Tuple tuple) {
    return executePreparedStatement(query, tuple, PreparedStatement::query);
  }

  private static Future<?> executePreparedStatement(Pool targetPool, String query, Tuple tuple) {
    return executePreparedStatement(targetPool, query, tuple, PreparedStatement::query);
  }

  private static Future<?> executePreparedStatement(
      String query,
      Tuple tuple,
      Function<PreparedStatement, PreparedQuery<?>> preparedQueryFactory) {
    return executePreparedStatement(pool, query, tuple, preparedQueryFactory);
  }

  private static Future<?> executePreparedStatement(
      Pool targetPool,
      String query,
      Tuple tuple,
      Function<PreparedStatement, PreparedQuery<?>> preparedQueryFactory) {
    return targetPool.withConnection(
        connection ->
            connection
                .prepare(query)
                .compose(
                    statement ->
                        preparedQueryFactory
                            .apply(statement)
                            .execute(tuple)
                            .compose(
                                rows -> statement.close().map(rows),
                                error ->
                                    statement
                                        .close()
                                        .compose(ignored -> Future.failedFuture(error)))));
  }

  @ParameterizedTest
  @MethodSource("batchScenarios")
  void testBatch(BatchScenario scenario) throws Exception {
    // recreate a fresh batch_test table for each scenario so that batch row ids can be reused
    // without worrying about collisions from previous scenarios
    recreateBatchTestTable();
    testing.waitForTraces(2);
    testing.clearData();

    // an empty batch is rejected before sending, so its execution fails; non-empty batches succeed
    try {
      testing
          .runWithSpan(
              "parent",
              () -> pool.preparedQuery(scenario.preparedQuery).executeBatch(scenario.tuples))
          .toCompletionStage()
          .toCompletableFuture()
          .get(30, SECONDS);
    } catch (ExecutionException ignored) {
      // an empty batch fails to execute; the failure is recorded on the client span
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? scenario.stableSpanName
                                : "INSERT tempdb.batch_test")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                maybeStable(DB_SYSTEM),
                                emitStableDatabaseSemconv() ? POSTGRESQL : null),
                            equalTo(maybeStable(DB_NAME), DB),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : USER_DB),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                emitStableDatabaseSemconv()
                                    ? scenario.preparedQuery
                                    : scenario.sanitizedQuery),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? scenario.querySummary : null),
                            equalTo(
                                DB_OPERATION_BATCH_SIZE,
                                emitStableDatabaseSemconv() ? scenario.batchSize : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "INSERT"),
                            equalTo(
                                maybeStable(DB_SQL_TABLE),
                                emitStableDatabaseSemconv() ? null : "batch_test"),
                            equalTo(
                                ERROR_TYPE,
                                emitStableDatabaseSemconv() ? scenario.errorType : null),
                            equalTo(maybeStablePeerService(), "test-peer-service"),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port))));
  }

  private static void recreateBatchTestTable() throws Exception {
    pool.query("drop table if exists batch_test")
        .execute()
        .compose(r -> pool.query("create table batch_test(id int primary key, num int)").execute())
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, SECONDS);
  }

  private static Stream<Arguments> batchScenarios() {
    return Stream.of(
        argumentSet(
            "empty",
            BatchScenario.builder()
                .preparedQuery("insert into batch_test values ($1, $2 + 1) returning *")
                .sanitizedQuery("insert into batch_test values ($1, $2 + ?) returning *")
                .tuples(emptyList())
                .stableSpanName("BATCH insert batch_test")
                .querySummary("BATCH insert batch_test")
                .batchSize(0)
                .errorType("io.vertx.core.VertxException")
                .build()),
        argumentSet(
            "single",
            BatchScenario.builder()
                .preparedQuery("insert into batch_test values ($1, $2 + 1) returning *")
                .sanitizedQuery("insert into batch_test values ($1, $2 + ?) returning *")
                .tuples(singletonList(Tuple.of(1, 1)))
                .stableSpanName("insert batch_test")
                .querySummary("insert batch_test")
                .build()),
        argumentSet(
            "twoSameOperation",
            BatchScenario.builder()
                .preparedQuery("insert into batch_test values ($1, $2 + 1) returning *")
                .sanitizedQuery("insert into batch_test values ($1, $2 + ?) returning *")
                .tuples(asList(Tuple.of(1, 1), Tuple.of(2, 2)))
                .stableSpanName("BATCH insert batch_test")
                .querySummary("BATCH insert batch_test")
                .batchSize(2)
                .build()));
  }

  @Test
  void testWithTransaction() throws Exception {
    testing
        .runWithSpan(
            "parent",
            () ->
                pool.withTransaction(
                    conn ->
                        conn.preparedQuery("select * from test where id = $1")
                            .execute(Tuple.of(1))))
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, SECONDS);

    assertPreparedSelect();
  }

  @Test
  void testWithConnection() throws Exception {
    testing
        .runWithSpan(
            "parent",
            () ->
                pool.withConnection(
                    conn ->
                        conn.preparedQuery("select * from test where id = $1")
                            .execute(Tuple.of(1))))
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, SECONDS);

    assertPreparedSelect();
  }

  @Test
  void testManyQueries() throws Exception {
    int count = 50;
    CountDownLatch latch = new CountDownLatch(count);
    List<CompletableFuture<Object>> futureList = new ArrayList<>();
    List<CompletableFuture<Object>> resultList = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      CompletableFuture<Object> future = new CompletableFuture<>();
      futureList.add(future);
      resultList.add(
          future.whenComplete((rows, throwable) -> testing.runWithSpan("callback", () -> {})));
    }
    for (CompletableFuture<Object> future : futureList) {
      testing.runWithSpan(
          "parent",
          () ->
              pool.query("select * from test")
                  .execute()
                  .onComplete(
                      rowSetAsyncResult -> {
                        if (rowSetAsyncResult.succeeded()) {
                          future.complete(rowSetAsyncResult.result());
                        } else {
                          future.completeExceptionally(rowSetAsyncResult.cause());
                        }
                        latch.countDown();
                      }));
    }
    assertThat(latch.await(30, SECONDS)).isTrue();
    for (CompletableFuture<Object> result : resultList) {
      result.get(10, SECONDS);
    }

    List<Consumer<TraceAssert>> assertions =
        Collections.nCopies(
            count,
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                    span ->
                        span.hasName(
                                emitStableDatabaseSemconv() ? "select test" : "SELECT tempdb.test")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(
                                    maybeStable(DB_SYSTEM),
                                    emitStableDatabaseSemconv() ? POSTGRESQL : null),
                                equalTo(maybeStable(DB_NAME), DB),
                                equalTo(DB_USER, emitStableDatabaseSemconv() ? null : USER_DB),
                                equalTo(maybeStable(DB_STATEMENT), "select * from test"),
                                equalTo(
                                    DB_QUERY_SUMMARY,
                                    emitStableDatabaseSemconv() ? "select test" : null),
                                equalTo(
                                    maybeStable(DB_OPERATION),
                                    emitStableDatabaseSemconv() ? null : "SELECT"),
                                equalTo(
                                    maybeStable(DB_SQL_TABLE),
                                    emitStableDatabaseSemconv() ? null : "test"),
                                equalTo(maybeStablePeerService(), "test-peer-service"),
                                equalTo(SERVER_ADDRESS, host),
                                equalTo(SERVER_PORT, port)),
                    span ->
                        span.hasName("callback")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
    testing.waitAndAssertTraces(assertions);
  }

  @Test
  void testConcurrency() throws Exception {
    int count = 50;
    CountDownLatch latch = new CountDownLatch(count);
    List<CompletableFuture<Object>> futureList = new ArrayList<>();
    List<CompletableFuture<Object>> resultList = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      CompletableFuture<Object> future = new CompletableFuture<>();
      futureList.add(future);
      resultList.add(
          future.whenComplete((rows, throwable) -> testing.runWithSpan("callback", () -> {})));
    }
    ExecutorService executorService = Executors.newFixedThreadPool(4);
    cleanup.deferCleanup(() -> executorService.shutdown());
    for (CompletableFuture<Object> future : futureList) {
      executorService.submit(
          () -> {
            testing.runWithSpan(
                "parent",
                () ->
                    pool.withConnection(
                            conn ->
                                conn.preparedQuery("select * from test where id = $1")
                                    .execute(Tuple.of(1)))
                        .onComplete(
                            rowSetAsyncResult -> {
                              if (rowSetAsyncResult.succeeded()) {
                                future.complete(rowSetAsyncResult.result());
                              } else {
                                future.completeExceptionally(rowSetAsyncResult.cause());
                              }
                              latch.countDown();
                            }));
          });
    }
    assertThat(latch.await(30, SECONDS)).isTrue();
    for (CompletableFuture<Object> result : resultList) {
      result.get(10, SECONDS);
    }

    List<Consumer<TraceAssert>> assertions =
        Collections.nCopies(
            count,
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                    span ->
                        span.hasName(
                                emitStableDatabaseSemconv() ? "select test" : "SELECT tempdb.test")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(
                                    maybeStable(DB_SYSTEM),
                                    emitStableDatabaseSemconv() ? POSTGRESQL : null),
                                equalTo(maybeStable(DB_NAME), DB),
                                equalTo(DB_USER, emitStableDatabaseSemconv() ? null : USER_DB),
                                equalTo(
                                    maybeStable(DB_STATEMENT), "select * from test where id = $1"),
                                equalTo(
                                    DB_QUERY_SUMMARY,
                                    emitStableDatabaseSemconv() ? "select test" : null),
                                equalTo(
                                    maybeStable(DB_OPERATION),
                                    emitStableDatabaseSemconv() ? null : "SELECT"),
                                equalTo(
                                    maybeStable(DB_SQL_TABLE),
                                    emitStableDatabaseSemconv() ? null : "test"),
                                equalTo(maybeStablePeerService(), "test-peer-service"),
                                equalTo(SERVER_ADDRESS, host),
                                equalTo(SERVER_PORT, port)),
                    span ->
                        span.hasName("callback")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
    testing.waitAndAssertTraces(assertions);
  }

  private static final class BatchScenario {
    final String preparedQuery;
    final String sanitizedQuery;
    final List<Tuple> tuples;
    final String stableSpanName;
    final String querySummary;
    final Long batchSize;
    final String errorType;

    BatchScenario(Builder builder) {
      this.preparedQuery = builder.preparedQuery;
      this.sanitizedQuery = builder.sanitizedQuery;
      this.tuples = builder.tuples;
      this.stableSpanName = builder.stableSpanName;
      this.querySummary = builder.querySummary;
      this.batchSize = builder.batchSize;
      this.errorType = builder.errorType;
    }

    static Builder builder() {
      return new Builder();
    }

    static final class Builder {
      private String preparedQuery;
      private String sanitizedQuery;
      private List<Tuple> tuples;
      private String stableSpanName;
      private String querySummary;
      private Long batchSize;
      private String errorType;

      Builder preparedQuery(String preparedQuery) {
        this.preparedQuery = preparedQuery;
        return this;
      }

      Builder sanitizedQuery(String sanitizedQuery) {
        this.sanitizedQuery = sanitizedQuery;
        return this;
      }

      Builder tuples(List<Tuple> tuples) {
        this.tuples = tuples;
        return this;
      }

      Builder stableSpanName(String stableSpanName) {
        this.stableSpanName = stableSpanName;
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

      Builder errorType(String errorType) {
        this.errorType = errorType;
        return this;
      }

      BatchScenario build() {
        return new BatchScenario(this);
      }
    }
  }
}
