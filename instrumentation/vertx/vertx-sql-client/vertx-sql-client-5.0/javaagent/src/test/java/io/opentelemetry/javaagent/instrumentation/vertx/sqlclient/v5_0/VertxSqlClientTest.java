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
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.oracleclient.OracleBuilder;
import io.vertx.oracleclient.OracleConnectOptions;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgException;
import io.vertx.pgclient.spi.PgDriver;
import io.vertx.sqlclient.ClientBuilder;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.PreparedQuery;
import io.vertx.sqlclient.PreparedStatement;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.SqlConnectOptions;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.impl.ClientBuilderBase;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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
  private static final ContextKey<String> REQUEST_KEY = ContextKey.named("test-request");

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

    testing.waitAndAssertTraces(
        trace -> assertServerListTarget(trace, host + ":" + port + "," + host + ":" + (port + 1)));
  }

  @Test
  void testConnectingToServerListWithUnixSocketOmitsStableTarget() throws Exception {
    PgConnectOptions first = connectOptions();
    PgConnectOptions second =
        new PgConnectOptions(first).setHost("/var/run/postgres:primary").setPort(5432);
    Pool listPool =
        PgBuilder.pool()
            .using(vertx)
            .connectingTo(asList(first, second))
            .with(new PoolOptions().setMaxSize(1))
            .build();
    cleanup.deferCleanup(listPool::close);

    select(listPool);

    testing.waitAndAssertTraces(trace -> assertServerListTarget(trace, null));
  }

  @Test
  void testConnectingToServerListFailureReportsTheWholeConfiguredTarget() {
    PgConnectOptions first = new PgConnectOptions(connectOptions()).setHost("127.0.0.1").setPort(1);
    PgConnectOptions second = new PgConnectOptions(first).setPort(2);
    Pool listPool =
        PgBuilder.pool()
            .using(vertx)
            .connectingTo(asList(first, second))
            .with(new PoolOptions().setMaxSize(1).setConnectionTimeout(5))
            .build();
    cleanup.deferCleanup(listPool::close);

    Throwable thrown = catchThrowable(() -> select(listPool));

    assertThat(thrown).isInstanceOf(ExecutionException.class);
    Throwable error = thrown.getCause();
    assertThat(error).isNotNull();
    testing.waitAndAssertTraces(
        trace ->
            assertServerListTarget(
                trace, "127.0.0.1", 1, "127.0.0.1:1,127.0.0.1:2", "select * from test", error));
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

    testing.waitAndAssertTraces(
        trace -> assertServerListTarget(trace, host + ":" + port + "," + host + ":" + (port + 1)));
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
  void testGenericPoolOptionsUseDriverDbSystem() throws Exception {
    SqlConnectOptions options = new PgConnectOptions(connectOptions()) {};
    Pool genericPool = Pool.pool(vertx, options, new PoolOptions().setMaxSize(1));
    cleanup.deferCleanup(genericPool::close);

    select(genericPool);

    testing.waitAndAssertTraces(VertxSqlClientTest::assertDirectTarget);
  }

  @Test
  void testNullServerDoesNotPoisonLaterPoolTarget() throws Exception {
    assertThatThrownBy(
            () -> Pool.pool(vertx, (SqlConnectOptions) null, new PoolOptions().setMaxSize(1)))
        .isInstanceOf(NullPointerException.class);

    Pool validPool = Pool.pool(vertx, connectOptions(), new PoolOptions().setMaxSize(1));
    cleanup.deferCleanup(validPool::close);

    select(validPool);

    testing.waitAndAssertTraces(VertxSqlClientTest::assertDirectTarget);
  }

  @Test
  void testFailedBuilderRestoresConnectHandler() throws Exception {
    CompletableFuture<Void> handlerInvoked = new CompletableFuture<>();
    Handler<SqlConnection> connectHandler =
        connection ->
            connection
                .query("select * from test")
                .execute()
                .onComplete(
                    result -> {
                      connection.close();
                      if (result.succeeded()) {
                        handlerInvoked.complete(null);
                      } else {
                        handlerInvoked.completeExceptionally(result.cause());
                      }
                    });
    ClientBuilder<Pool> builder =
        PgBuilder.pool()
            .using(vertx)
            .connectingTo(connectOptions().setHost("failed.example"))
            .withConnectHandler(connectHandler)
            .with(new PoolOptions().setIdleTimeoutUnit(null));
    Field connectHandlerField = ClientBuilderBase.class.getDeclaredField("connectHandler");
    connectHandlerField.setAccessible(true);

    assertThatThrownBy(builder::build).isInstanceOf(NullPointerException.class);
    assertThat(connectHandlerField.get(builder)).isSameAs(connectHandler);

    PgConnectOptions first = connectOptions();
    PgConnectOptions second = new PgConnectOptions(first).setPort(port + 1);
    Pool rebuiltPool =
        builder.connectingTo(asList(first, second)).with(new PoolOptions().setMaxSize(1)).build();
    cleanup.deferCleanup(rebuiltPool::close);
    assertThat(connectHandlerField.get(builder)).isSameAs(connectHandler);

    SqlConnection connection =
        rebuiltPool.getConnection().toCompletionStage().toCompletableFuture().get(30, SECONDS);
    cleanup.deferCleanup(connection::close);
    handlerInvoked.get(30, SECONDS);

    testing.waitAndAssertTraces(
        trace -> assertServerListTarget(trace, host + ":" + port + "," + host + ":" + (port + 1)));
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
    Future<?> secondResult = supplierPool.query("select * from pg_database").execute();
    suppliedOptions.complete(connectOptions());
    Future.all(firstResult, secondResult)
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, SECONDS);

    assertThat(calls).hasValue(1);
    testing.waitAndAssertSortedTraces(
        comparingRootSpanAttribute(maybeStable(DB_STATEMENT)),
        trace ->
            assertSupplierQuery(
                trace, "select * from pg_database", "pg_database", POSTGRESQL, host, null),
        VertxSqlClientTest::assertSupplierTarget);
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
    Future<?> secondResult = supplierPool.query("select * from pg_database").execute();
    secondOptions.complete(second);
    firstOptions.complete(first);
    Future.all(firstResult, secondResult)
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, SECONDS);

    assertThat(calls).hasValue(2);
    testing.waitAndAssertSortedTraces(
        comparingRootSpanAttribute(maybeStable(DB_STATEMENT)),
        trace ->
            assertSupplierQuery(
                trace, "select * from pg_database", "pg_database", POSTGRESQL, alternateHost, null),
        trace -> assertSupplierTarget(trace, host));
  }

  @Test
  void testConcurrentSupplierPoolsCanShareOnePendingFuture() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    Promise<SqlConnectOptions> suppliedOptions = Promise.promise();
    Supplier<Future<SqlConnectOptions>> supplier =
        () -> {
          calls.incrementAndGet();
          return suppliedOptions.future();
        };
    Pool firstSupplierPool =
        PgBuilder.pool()
            .using(vertx)
            .connectingTo(supplier)
            .with(new PoolOptions().setMaxSize(1))
            .build();
    cleanup.deferCleanup(firstSupplierPool::close);
    Pool secondSupplierPool =
        PgBuilder.pool()
            .using(vertx)
            .connectingTo(supplier)
            .with(new PoolOptions().setMaxSize(1))
            .build();
    cleanup.deferCleanup(secondSupplierPool::close);

    Future<?> firstResult = firstSupplierPool.query("select * from test").execute();
    Future<?> secondResult = secondSupplierPool.query("select * from test").execute();
    suppliedOptions.complete(connectOptions());
    Future.all(firstResult, secondResult)
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, SECONDS);

    assertThat(calls).hasValue(2);
    testing.waitAndAssertTraces(
        VertxSqlClientTest::assertSupplierTarget, VertxSqlClientTest::assertSupplierTarget);
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
  void testUnknownDriverSupplierFailureUsesFallbackDbSystem() {
    RuntimeException thrown = new RuntimeException("supplier failed");
    Pool throwingPool =
        ClientBuilder.pool(new PgDriver() {})
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
    Pool supplierPool =
        ClientBuilder.pool(new PgDriver() {})
            .using(vertx)
            .connectingTo(() -> Future.failedFuture(failed))
            .with(new PoolOptions().setMaxSize(1))
            .build();
    cleanup.deferCleanup(supplierPool::close);

    assertThatThrownBy(() -> select(supplierPool)).hasCause(failed);

    testing.waitAndAssertTraces(
        trace -> assertSupplierFailure(trace, thrown, "other_sql"),
        trace -> assertSupplierFailure(trace, failed, "other_sql"));
  }

  @Test
  void testUnknownDriverSupplierConnectFailureCapturesSuppliedOptions() {
    RuntimeException failed = new RuntimeException("connection failed");
    Pool supplierPool =
        ClientBuilder.pool(failingDriver(failed))
            .using(vertx)
            .connectingTo(() -> Future.succeededFuture(connectOptions()))
            .with(new PoolOptions().setMaxSize(1))
            .build();
    cleanup.deferCleanup(supplierPool::close);

    assertThatThrownBy(() -> select(supplierPool)).hasCause(failed);

    testing.waitAndAssertTraces(trace -> assertSupplierFailure(trace, failed, "other_sql", host));
  }

  @Test
  void testStandaloneConnectionFailureDoesNotPoisonLaterQuery() {
    RuntimeException failed = new RuntimeException("connection failed");
    AtomicInteger calls = new AtomicInteger();
    PgConnectOptions staleOptions = new PgConnectOptions(connectOptions()).setHost("stale.example");
    Pool supplierPool =
        ClientBuilder.pool(failingDriver(failed))
            .using(vertx)
            .connectingTo(
                () ->
                    Future.succeededFuture(
                        calls.getAndIncrement() == 0 ? staleOptions : connectOptions()))
            .with(new PoolOptions().setMaxSize(1))
            .build();
    cleanup.deferCleanup(supplierPool::close);

    assertThatThrownBy(
            () ->
                supplierPool
                    .getConnection()
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(30, SECONDS))
        .hasCause(failed);
    assertThatThrownBy(() -> select(supplierPool)).hasCause(failed);

    assertThat(calls).hasValue(2);
    testing.waitAndAssertTraces(trace -> assertSupplierFailure(trace, failed, "other_sql", host));
  }

  @Test
  void testSupplierAddressDoesNotBecomeFallbackSpanName() {
    RuntimeException failed = new RuntimeException("connection failed");
    PgConnectOptions suppliedOptions =
        new PgConnectOptions(connectOptions()).setDatabase("").setUser("");
    Pool supplierPool =
        ClientBuilder.pool(failingDriver(failed))
            .using(vertx)
            .connectingTo(() -> Future.succeededFuture(suppliedOptions))
            .with(new PoolOptions().setMaxSize(1))
            .build();
    cleanup.deferCleanup(supplierPool::close);

    assertThatThrownBy(
            () ->
                supplierPool
                    .query("")
                    .execute()
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(30, SECONDS))
        .hasCause(failed);

    testing.waitAndAssertTraces(trace -> assertSupplierFallbackFailure(trace, failed));
  }

  @Test
  void testConcurrentSupplierFailuresKeepTheirOptions() throws Exception {
    RuntimeException firstFailure = new IllegalArgumentException("first connection failed");
    RuntimeException secondFailure = new IllegalStateException("second connection failed");
    AtomicInteger calls = new AtomicInteger();
    PgConnectOptions firstOptions = new PgConnectOptions(connectOptions()).setHost("first.example");
    List<Promise<SqlConnection>> connectionAttempts = new CopyOnWriteArrayList<>();
    CountDownLatch attemptsStarted = new CountDownLatch(2);
    Pool supplierPool =
        ClientBuilder.pool(
                driver(
                    options -> {
                      Promise<SqlConnection> attempt = Promise.promise();
                      connectionAttempts.add(attempt);
                      attemptsStarted.countDown();
                      return attempt.future();
                    }))
            .using(vertx)
            .connectingTo(
                () ->
                    Future.succeededFuture(
                        calls.getAndIncrement() == 0 ? firstOptions : connectOptions()))
            .with(new PoolOptions().setMaxSize(2))
            .build();
    cleanup.deferCleanup(supplierPool::close);

    Future<?> firstResult = supplierPool.query("select * from first_request").execute();
    Future<?> secondResult = supplierPool.query("select * from second_request").execute();
    assertThat(attemptsStarted.await(30, SECONDS)).isTrue();
    connectionAttempts.get(1).fail(secondFailure);
    connectionAttempts.get(0).fail(firstFailure);

    assertThatThrownBy(() -> firstResult.toCompletionStage().toCompletableFuture().get(30, SECONDS))
        .hasCause(firstFailure);
    assertThatThrownBy(
            () -> secondResult.toCompletionStage().toCompletableFuture().get(30, SECONDS))
        .hasCause(secondFailure);
    assertThat(calls).hasValue(2);
    testing.waitAndAssertSortedTraces(
        comparingRootSpanAttribute(maybeStable(DB_STATEMENT)),
        trace ->
            assertSupplierQuery(
                trace,
                "select * from first_request",
                "first_request",
                "other_sql",
                "first.example",
                firstFailure),
        trace ->
            assertSupplierQuery(
                trace,
                "select * from second_request",
                "second_request",
                "other_sql",
                host,
                secondFailure));
  }

  @Test
  void testConcurrentAdmissionKeepsSupplierAttemptWithItsQuery() throws Exception {
    RuntimeException firstFailure = new IllegalArgumentException("first request failed");
    RuntimeException secondFailure = new IllegalStateException("second request failed");
    Promise<SqlConnection> firstAttempt = Promise.promise();
    Promise<SqlConnection> secondAttempt = Promise.promise();
    CountDownLatch firstScheduled = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    CountDownLatch secondConnecting = new CountDownLatch(1);
    CountDownLatch firstConnecting = new CountDownLatch(1);
    AtomicInteger schedules = new AtomicInteger();
    AtomicInteger calls = new AtomicInteger();
    List<String> targets = new CopyOnWriteArrayList<>();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    cleanup.deferCleanup(executor::shutdownNow);
    cleanup.deferCleanup(releaseFirst::countDown);
    Pool supplierPool =
        ClientBuilder.pool(
                TestPgDriver.create(
                    options -> {
                      targets.add(options.getHost());
                      if (options.getHost().equals("second.example")) {
                        secondConnecting.countDown();
                        return secondAttempt.future();
                      }
                      firstConnecting.countDown();
                      return firstAttempt.future();
                    },
                    () -> {
                      if (schedules.getAndIncrement() == 0) {
                        firstScheduled.countDown();
                        try {
                          assertThat(releaseFirst.await(30, SECONDS)).isTrue();
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                          throw new AssertionError(e);
                        }
                      }
                    },
                    ignored -> {}))
            .using(vertx)
            .connectingTo(
                () ->
                    Future.succeededFuture(
                        connectOptions()
                            .setHost(
                                calls.getAndIncrement() == 0 ? "second.example" : "first.example")))
            .with(new PoolOptions().setMaxSize(2))
            .build();
    cleanup.deferCleanup(supplierPool::close);

    java.util.concurrent.Future<Future<?>> firstSubmission =
        executor.submit(() -> supplierPool.query("select * from first_request").execute());
    assertThat(firstScheduled.await(30, SECONDS)).isTrue();
    Future<?> secondResult = supplierPool.query("select * from second_request").execute();
    assertThat(secondConnecting.await(30, SECONDS)).isTrue();
    assertThat(firstConnecting.getCount()).isEqualTo(1);
    releaseFirst.countDown();
    Future<?> firstResult = firstSubmission.get(30, SECONDS);
    assertThat(firstConnecting.await(30, SECONDS)).isTrue();
    assertThat(targets).containsExactly("second.example", "first.example");

    firstAttempt.fail(firstFailure);
    secondAttempt.fail(secondFailure);
    assertThatThrownBy(() -> firstResult.toCompletionStage().toCompletableFuture().get(30, SECONDS))
        .hasCause(firstFailure);
    assertThatThrownBy(
            () -> secondResult.toCompletionStage().toCompletableFuture().get(30, SECONDS))
        .hasCause(secondFailure);
    assertThat(calls).hasValue(2);
    testing.waitAndAssertSortedTraces(
        comparingRootSpanAttribute(maybeStable(DB_STATEMENT)),
        trace ->
            assertSupplierQuery(
                trace,
                "select * from first_request",
                "first_request",
                "other_sql",
                "first.example",
                firstFailure),
        trace ->
            assertSupplierQuery(
                trace,
                "select * from second_request",
                "second_request",
                "other_sql",
                "second.example",
                secondFailure));
  }

  @Test
  void testQueuedReplacementStartsBeforePreviousFailureIsDelivered() throws Exception {
    RuntimeException firstFailure = new IllegalArgumentException("first connection failed");
    RuntimeException secondFailure = new IllegalStateException("replacement connection failed");
    Promise<SqlConnection> firstAttempt = Promise.promise();
    Promise<SqlConnection> secondAttempt = Promise.promise();
    CountDownLatch firstConnecting = new CountDownLatch(1);
    CountDownLatch replacementConnecting = new CountDownLatch(1);
    AtomicInteger calls = new AtomicInteger();
    AtomicInteger completions = new AtomicInteger();
    List<String> events = new CopyOnWriteArrayList<>();
    Pool supplierPool =
        ClientBuilder.pool(
                driver(
                    options -> {
                      if (options.getHost().equals("first.example")) {
                        firstConnecting.countDown();
                        return firstAttempt.future();
                      }
                      events.add("replacement connecting");
                      replacementConnecting.countDown();
                      return secondAttempt.future();
                    }))
            .using(vertx)
            .connectingTo(
                () ->
                    Future.succeededFuture(
                        connectOptions()
                            .setHost(
                                calls.getAndIncrement() == 0 ? "first.example" : "second.example")))
            .with(new PoolOptions().setMaxSize(1))
            .build();
    cleanup.deferCleanup(supplierPool::close);

    Context context = vertx.getOrCreateContext();
    CompletableFuture<Throwable> firstResult =
        queryOnContext(supplierPool, "select * from first_request", context, completions);
    firstResult.thenRun(() -> events.add("first failure delivered"));
    assertThat(firstConnecting.await(30, SECONDS)).isTrue();
    CompletableFuture<Throwable> secondResult =
        queryOnContext(supplierPool, "select * from second_request", context, completions);
    CompletableFuture<Void> queued = new CompletableFuture<>();
    context.runOnContext(ignored -> queued.complete(null));
    queued.get(30, SECONDS);
    assertThat(calls).hasValue(1);

    firstAttempt.fail(firstFailure);
    assertThat(replacementConnecting.await(30, SECONDS)).isTrue();
    assertThat(firstResult.get(30, SECONDS)).isSameAs(firstFailure);
    secondAttempt.fail(secondFailure);
    assertThat(secondResult.get(30, SECONDS)).isSameAs(secondFailure);
    assertThat(events).containsExactly("replacement connecting", "first failure delivered");
    assertThat(completions).hasValue(2);
    assertThat(calls).hasValue(2);
    testing.waitAndAssertSortedTraces(
        comparingRootSpanAttribute(maybeStable(DB_STATEMENT)),
        trace ->
            assertSupplierQuery(
                trace,
                "select * from first_request",
                "first_request",
                "other_sql",
                "first.example",
                firstFailure),
        trace ->
            assertSupplierQuery(
                trace,
                "select * from second_request",
                "second_request",
                "other_sql",
                "second.example",
                secondFailure));
  }

  @Test
  void testExplicitAcquisitionDoesNotTakeQueuedQueryTarget() throws Exception {
    RuntimeException acquisitionFailure =
        new IllegalArgumentException("explicit acquisition failed");
    RuntimeException queryFailure = new IllegalStateException("query acquisition failed");
    Promise<SqlConnection> acquisitionAttempt = Promise.promise();
    Promise<SqlConnection> queryAttempt = Promise.promise();
    AtomicInteger calls = new AtomicInteger();
    CountDownLatch queryConnecting = new CountDownLatch(1);
    Pool supplierPool =
        ClientBuilder.pool(
                driver(
                    options -> {
                      if (options.getHost().equals("explicit.example")) {
                        return acquisitionAttempt.future();
                      }
                      queryConnecting.countDown();
                      return queryAttempt.future();
                    }))
            .using(vertx)
            .connectingTo(
                () ->
                    Future.succeededFuture(
                        connectOptions()
                            .setHost(
                                calls.getAndIncrement() == 0
                                    ? "explicit.example"
                                    : "query.example")))
            .with(new PoolOptions().setMaxSize(1))
            .build();
    cleanup.deferCleanup(supplierPool::close);

    Future<SqlConnection> acquisition = supplierPool.getConnection();
    Future<?> query = supplierPool.query("select * from query_request").execute();
    assertThat(calls).hasValue(1);
    acquisitionAttempt.fail(acquisitionFailure);
    assertThat(queryConnecting.await(30, SECONDS)).isTrue();
    queryAttempt.fail(queryFailure);
    assertThatThrownBy(() -> acquisition.toCompletionStage().toCompletableFuture().get(30, SECONDS))
        .hasCause(acquisitionFailure);
    assertThatThrownBy(() -> query.toCompletionStage().toCompletableFuture().get(30, SECONDS))
        .hasCause(queryFailure);
    assertThat(calls).hasValue(2);
    testing.waitAndAssertTraces(
        trace ->
            assertSupplierQuery(
                trace,
                "select * from query_request",
                "query_request",
                "other_sql",
                "query.example",
                queryFailure));
  }

  @Test
  void testReusedConnectionKeepsItsTargetWhileAnotherAttemptStarts() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    Promise<SqlConnectOptions> laterOptions = Promise.promise();
    CountDownLatch laterConnecting = new CountDownLatch(1);
    String alternateHost = host.equals("localhost") ? "127.0.0.1" : "localhost";
    Pool supplierPool =
        PgBuilder.pool()
            .using(vertx)
            .connectingTo(
                () -> {
                  if (calls.getAndIncrement() == 0) {
                    return Future.succeededFuture(connectOptions());
                  }
                  laterConnecting.countDown();
                  return laterOptions.future();
                })
            .with(new PoolOptions().setMaxSize(2))
            .build();
    cleanup.deferCleanup(supplierPool::close);

    SqlConnection connection =
        supplierPool.getConnection().toCompletionStage().toCompletableFuture().get(30, SECONDS);
    Future<?> laterResult = supplierPool.query("select * from pg_database").execute();
    assertThat(laterConnecting.await(30, SECONDS)).isTrue();
    connection.close().toCompletionStage().toCompletableFuture().get(30, SECONDS);
    select(supplierPool);
    assertThat(laterResult.isComplete()).isFalse();
    assertThat(calls).hasValue(2);
    laterOptions.complete(connectOptions().setHost(alternateHost));
    laterResult.toCompletionStage().toCompletableFuture().get(30, SECONDS);

    testing.waitAndAssertSortedTraces(
        comparingRootSpanAttribute(maybeStable(DB_STATEMENT)),
        trace ->
            assertSupplierQuery(
                trace, "select * from pg_database", "pg_database", POSTGRESQL, alternateHost, null),
        trace -> assertSupplierTarget(trace, host));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void testSupplierQueryTimeoutFreezesTargetBeforeLateFailure(boolean optionsBeforeTimeout)
      throws Exception {
    assumeTrue(testLatestDeps());
    RuntimeException lateFailure = new IllegalArgumentException("late connection failure");
    Promise<SqlConnectOptions> options = Promise.promise();
    Promise<SqlConnection> attempt = Promise.promise();
    CountDownLatch supplierCalled = new CountDownLatch(1);
    CountDownLatch connecting = new CountDownLatch(1);
    CompletableFuture<Throwable> lateCompletion = new CompletableFuture<>();
    AtomicInteger completions = new AtomicInteger();
    AtomicInteger poolCompletions = new AtomicInteger();
    Context context = vertx.getOrCreateContext();
    Pool supplierPool =
        ClientBuilder.pool(
                TestPgDriver.create(
                    supplied -> {
                      connecting.countDown();
                      return attempt.future();
                    },
                    () -> {},
                    error -> {
                      poolCompletions.incrementAndGet();
                      if (error == lateFailure) {
                        lateCompletion.complete(error);
                      }
                    }))
            .using(vertx)
            .connectingTo(
                () -> {
                  supplierCalled.countDown();
                  return options.future();
                })
            .with(
                new PoolOptions()
                    .setMaxSize(1)
                    .setEventLoopSize(2)
                    .setConnectionTimeout(1)
                    .setConnectionTimeoutUnit(SECONDS))
            .build();
    cleanup.deferCleanup(supplierPool::close);
    if (optionsBeforeTimeout) {
      options.complete(connectOptions().setHost("timeout.example"));
    }

    CompletableFuture<Throwable> result =
        queryOnContext(supplierPool, "select * from timeout_request", context, completions);
    assertThat(supplierCalled.await(30, SECONDS)).isTrue();
    if (optionsBeforeTimeout) {
      assertThat(connecting.await(30, SECONDS)).isTrue();
      assertThat(result.isDone()).isFalse();
    }
    Throwable timeout = result.get(30, SECONDS);
    assertThat(timeout).hasMessage("Timeout waiting for connection");
    testing.waitAndAssertTraces(
        trace ->
            assertSupplierQuery(
                trace,
                "select * from timeout_request",
                "timeout_request",
                "other_sql",
                null,
                timeout));
    if (!optionsBeforeTimeout) {
      options.complete(connectOptions().setHost("late.example"));
    }
    assertThat(connecting.await(30, SECONDS)).isTrue();
    attempt.fail(lateFailure);
    assertThat(lateCompletion.get(30, SECONDS)).isSameAs(lateFailure);
    assertThat(completions).hasValue(1);
    assertThat(poolCompletions).hasValue(2);
    assertThat(result.get(30, SECONDS)).isSameAs(timeout);
    testing.waitAndAssertTraces(
        trace ->
            assertSupplierQuery(
                trace,
                "select * from timeout_request",
                "timeout_request",
                "other_sql",
                null,
                timeout));
  }

  @Test
  void testSupplierFailureBeforeTimeoutKeepsTargetAndError() throws Exception {
    assumeTrue(testLatestDeps());
    RuntimeException failure = new IllegalArgumentException("connection failed before timeout");
    CompletableFuture<Throwable> timeoutCompletion = new CompletableFuture<>();
    AtomicInteger completions = new AtomicInteger();
    AtomicInteger poolCompletions = new AtomicInteger();
    Context context = vertx.getOrCreateContext();
    Pool supplierPool =
        ClientBuilder.pool(
                TestPgDriver.create(
                    supplied -> Future.failedFuture(failure),
                    () -> {},
                    error -> {
                      poolCompletions.incrementAndGet();
                      if (error != failure) {
                        timeoutCompletion.complete(error);
                      }
                    }))
            .using(vertx)
            .connectingTo(() -> Future.succeededFuture(connectOptions().setHost("failed.example")))
            .with(
                new PoolOptions()
                    .setMaxSize(1)
                    .setEventLoopSize(2)
                    .setConnectionTimeout(1)
                    .setConnectionTimeoutUnit(SECONDS))
            .build();
    cleanup.deferCleanup(supplierPool::close);

    CompletableFuture<Throwable> result =
        queryOnContext(supplierPool, "select * from failed_request", context, completions);
    assertThat(result.get(30, SECONDS)).isSameAs(failure);
    testing.waitAndAssertTraces(
        trace ->
            assertSupplierQuery(
                trace,
                "select * from failed_request",
                "failed_request",
                "other_sql",
                "failed.example",
                failure));

    assertThat(timeoutCompletion.get(30, SECONDS)).hasMessage("Timeout waiting for connection");
    assertThat(result.get(30, SECONDS)).isSameAs(failure);
    assertThat(completions).hasValue(1);
    assertThat(poolCompletions).hasValue(2);
    testing.waitAndAssertTraces(
        trace ->
            assertSupplierQuery(
                trace,
                "select * from failed_request",
                "failed_request",
                "other_sql",
                "failed.example",
                failure));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void testFixedOptionsTimeoutRetainsConfiguredTarget(boolean serverList) throws Exception {
    assumeTrue(testLatestDeps());
    RuntimeException lateFailure = new IllegalArgumentException("late fixed connection failure");
    Promise<SqlConnection> attempt = Promise.promise();
    CountDownLatch connecting = new CountDownLatch(1);
    CompletableFuture<Throwable> lateCompletion = new CompletableFuture<>();
    AtomicInteger completions = new AtomicInteger();
    AtomicInteger poolCompletions = new AtomicInteger();
    PgConnectOptions first = connectOptions().setHost("fixed.example");
    PgConnectOptions second = connectOptions().setHost("other.example").setPort(port + 1);
    ClientBuilder<Pool> builder =
        ClientBuilder.pool(
                TestPgDriver.create(
                    options -> {
                      connecting.countDown();
                      return attempt.future();
                    },
                    () -> {},
                    error -> {
                      poolCompletions.incrementAndGet();
                      if (error == lateFailure) {
                        lateCompletion.complete(error);
                      }
                    }))
            .using(vertx)
            .with(
                new PoolOptions()
                    .setMaxSize(1)
                    .setEventLoopSize(2)
                    .setConnectionTimeout(1)
                    .setConnectionTimeoutUnit(SECONDS));
    Pool fixedPool =
        serverList
            ? builder.connectingTo(asList(first, second)).build()
            : builder.connectingTo(first).build();
    cleanup.deferCleanup(
        () -> fixedPool.close().toCompletionStage().toCompletableFuture().get(30, SECONDS));

    try {
      CompletableFuture<Throwable> result =
          queryOnContext(
              fixedPool, "select * from fixed_request", vertx.getOrCreateContext(), completions);
      assertThat(connecting.await(30, SECONDS)).isTrue();
      assertThat(attempt.future().isComplete()).isFalse();
      Throwable timeout = result.get(30, SECONDS);
      assertThat(timeout).hasMessage("Timeout waiting for connection");
      Consumer<TraceAssert> assertion =
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName(
                              emitStableDatabaseSemconv()
                                  ? "select fixed_request"
                                  : "SELECT tempdb.fixed_request")
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasStatus(StatusData.error())
                          .hasEventsSatisfyingExactly(
                              event ->
                                  event
                                      .hasName("exception")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(EXCEPTION_TYPE, timeout.getClass().getName()),
                                          equalTo(EXCEPTION_MESSAGE, timeout.getMessage()),
                                          satisfies(
                                              EXCEPTION_STACKTRACE,
                                              val -> val.isInstanceOf(String.class))))
                          .hasAttributesSatisfyingExactly(
                              equalTo(
                                  DB_SYSTEM_NAME, emitStableDatabaseSemconv() ? "other_sql" : null),
                              equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? DB : null),
                              equalTo(
                                  DB_QUERY_TEXT,
                                  emitStableDatabaseSemconv()
                                      ? "select * from fixed_request"
                                      : null),
                              equalTo(
                                  DB_QUERY_SUMMARY,
                                  emitStableDatabaseSemconv() ? "select fixed_request" : null),
                              equalTo(DB_NAME, emitOldDatabaseSemconv() ? DB : null),
                              equalTo(DB_USER, emitOldDatabaseSemconv() ? USER_DB : null),
                              equalTo(
                                  DB_STATEMENT,
                                  emitOldDatabaseSemconv() ? "select * from fixed_request" : null),
                              equalTo(DB_OPERATION, emitOldDatabaseSemconv() ? "SELECT" : null),
                              equalTo(
                                  DB_SQL_TABLE, emitOldDatabaseSemconv() ? "fixed_request" : null),
                              equalTo(
                                  SERVER_ADDRESS,
                                  emitStableDatabaseSemconv() && serverList
                                      ? "fixed.example:" + port + ",other.example:" + (port + 1)
                                      : "fixed.example"),
                              equalTo(
                                  SERVER_PORT,
                                  emitStableDatabaseSemconv() && serverList
                                      ? null
                                      : Long.valueOf(port)),
                              equalTo(
                                  ERROR_TYPE,
                                  emitStableDatabaseSemconv()
                                      ? timeout.getClass().getName()
                                      : null)));
      testing.waitAndAssertTraces(assertion);

      attempt.fail(lateFailure);
      assertThat(lateCompletion.get(30, SECONDS)).isSameAs(lateFailure);
      assertThat(result.get(30, SECONDS)).isSameAs(timeout);
      assertThat(completions).hasValue(1);
      assertThat(poolCompletions).hasValue(2);
      testing.waitAndAssertTraces(assertion);
    } finally {
      attempt.tryFail(lateFailure);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void testSynchronousSupplierThrowOverlappingQueryTimeoutEndsSpanOnce(boolean throwBeforeTimeout)
      throws Exception {
    assumeTrue(testLatestDeps());
    Vertx isolatedVertx = Vertx.vertx();
    cleanup.deferCleanup(
        () -> isolatedVertx.close().toCompletionStage().toCompletableFuture().get(30, SECONDS));
    RuntimeException thrown = new IllegalStateException("synchronous supplier failure");
    CountDownLatch supplierEntered = new CountDownLatch(1);
    CountDownLatch releaseSupplier = new CountDownLatch(1);
    CompletableFuture<Throwable> timeoutCompletion = new CompletableFuture<>();
    AtomicInteger poolCompletions = new AtomicInteger();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    cleanup.deferCleanup(executor::shutdownNow);
    cleanup.deferCleanup(releaseSupplier::countDown);
    TestPgDriver driver =
        TestPgDriver.create(
            options -> Future.failedFuture(new AssertionError("Unexpected connection")),
            () -> {},
            error -> {
              poolCompletions.incrementAndGet();
              timeoutCompletion.complete(error);
            });
    Pool supplierPool =
        ClientBuilder.pool(driver)
            .using(isolatedVertx)
            .connectingTo(
                () -> {
                  supplierEntered.countDown();
                  try {
                    assertThat(releaseSupplier.await(30, SECONDS)).isTrue();
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                  }
                  throw thrown;
                })
            .with(
                new PoolOptions()
                    .setMaxSize(1)
                    .setEventLoopSize(2)
                    .setConnectionTimeout(1)
                    .setConnectionTimeoutUnit(SECONDS))
            .build();

    java.util.concurrent.Future<Throwable> result =
        executor.submit(
            () ->
                catchThrowable(
                    () -> supplierPool.query("select * from timeout_request").execute()));
    cleanup.deferCleanup(
        () -> {
          releaseSupplier.countDown();
          try {
            result.get(30, SECONDS);
          } finally {
            driver
                .closeAfterSupplierThrow()
                .toCompletionStage()
                .toCompletableFuture()
                .get(30, SECONDS);
          }
        });
    assertThat(supplierEntered.await(30, SECONDS)).isTrue();
    if (throwBeforeTimeout) {
      releaseSupplier.countDown();
      assertThat(result.get(30, SECONDS)).isSameAs(thrown);
    }
    Throwable timeout = timeoutCompletion.get(30, SECONDS);
    assertThat(timeout).hasMessage("Timeout waiting for connection");
    releaseSupplier.countDown();
    assertThat(result.get(30, SECONDS)).isSameAs(thrown);
    assertThat(poolCompletions).hasValue(1);
    testing.waitAndAssertTraces(
        trace ->
            assertSupplierQuery(
                trace,
                "select * from timeout_request",
                "timeout_request",
                "other_sql",
                null,
                throwBeforeTimeout ? thrown : timeout));
  }

  @Test
  void testOracleSupplierConnectFailureCapturesSuppliedOptions() {
    OracleConnectOptions options =
        new OracleConnectOptions()
            .setHost("127.0.0.1")
            .setPort(1)
            .setDatabase("testdb")
            .setUser("testuser")
            .setPassword("testpassword");
    Pool oraclePool =
        OracleBuilder.pool()
            .using(vertx)
            .connectingTo(() -> Future.succeededFuture(options))
            .with(new PoolOptions().setMaxSize(1).setConnectionTimeout(5))
            .build();
    cleanup.deferCleanup(oraclePool::close);

    Throwable thrown = catchThrowable(() -> select(oraclePool));

    assertThat(thrown).isInstanceOf(ExecutionException.class);
    Throwable error = thrown.getCause();
    assertThat(error).isNotNull();
    testing.waitAndAssertTraces(trace -> assertOracleConnectFailure(trace, error));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void testExhaustedFixedPoolTimeoutRetainsConfiguredTarget(boolean serverList) throws Exception {
    assumeTrue(testLatestDeps());
    PgConnectOptions first = connectOptions();
    PgConnectOptions second = new PgConnectOptions(first).setPort(port + 1);
    ClientBuilder<Pool> builder =
        PgBuilder.pool()
            .using(vertx)
            .with(
                new PoolOptions()
                    .setMaxSize(1)
                    .setConnectionTimeout(1)
                    .setConnectionTimeoutUnit(SECONDS));
    Pool fixedPool =
        serverList
            ? builder.connectingTo(asList(first, second)).build()
            : builder.connectingTo(first).build();
    cleanup.deferCleanup(
        () -> fixedPool.close().toCompletionStage().toCompletableFuture().get(30, SECONDS));
    SqlConnection connection =
        fixedPool.getConnection().toCompletionStage().toCompletableFuture().get(30, SECONDS);
    cleanup.deferCleanup(
        () -> connection.close().toCompletionStage().toCompletableFuture().get(30, SECONDS));

    Throwable thrown = catchThrowable(() -> select(fixedPool));

    assertThat(thrown).isInstanceOf(ExecutionException.class);
    Throwable timeout = thrown.getCause();
    assertThat(timeout).hasMessage("Timeout waiting for connection");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "select test" : "SELECT tempdb.test")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(EXCEPTION_TYPE, timeout.getClass().getName()),
                                        equalTo(EXCEPTION_MESSAGE, timeout.getMessage()),
                                        satisfies(
                                            EXCEPTION_STACKTRACE,
                                            val -> val.isInstanceOf(String.class))))
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
                            equalTo(
                                maybeStablePeerService(),
                                emitStableDatabaseSemconv() && serverList
                                    ? null
                                    : "test-peer-service"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv() && serverList
                                    ? host + ":" + port + "," + host + ":" + (port + 1)
                                    : host),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv() && serverList
                                    ? null
                                    : Long.valueOf(port)),
                            equalTo(
                                ERROR_TYPE,
                                emitStableDatabaseSemconv()
                                    ? timeout.getClass().getName()
                                    : null))));
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

    executePreparedStatement(listPool, query, Tuple.of(1), PreparedStatement::query)
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, SECONDS);

    testing.waitAndAssertTraces(
        trace ->
            assertServerListTarget(
                trace, query, host + ":" + port + "," + host + ":" + (port + 1)));
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
        trace -> assertServerListTarget(trace, host + ":" + port + "," + host + ":" + (port + 1)),
        trace -> assertServerListTarget(trace, host + ":" + port + "," + host + ":" + (port + 2)));
  }

  @Test
  void testMutableServerListIsSnapshottedForEachBuild() throws Exception {
    PgConnectOptions first = connectOptions();
    String alternateHost = host.equals("localhost") ? "127.0.0.1" : "localhost";
    PgConnectOptions second = new PgConnectOptions(first).setHost(alternateHost);
    List<SqlConnectOptions> databases = new ArrayList<>(asList(first, second));
    ClientBuilder<Pool> builder =
        PgBuilder.pool().using(vertx).connectingTo(databases).with(new PoolOptions().setMaxSize(1));

    Pool firstPool = builder.build();
    cleanup.deferCleanup(firstPool::close);
    select(firstPool);
    testing.waitForTraces(1);
    testing.clearData();

    first.setHost("mutated-first.example");
    second.setHost("mutated-second.example");
    databases.set(0, connectOptions());
    databases.set(1, new PgConnectOptions(connectOptions()));
    Pool secondPool = builder.build();
    cleanup.deferCleanup(secondPool::close);

    select(firstPool);
    select(secondPool);

    testing.waitAndAssertTraces(
        trace ->
            assertServerListTarget(trace, host + ":" + port + "," + alternateHost + ":" + port),
        trace -> assertServerListTarget(trace, host + ":" + port + "," + host + ":" + port));
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
                            equalTo(SERVER_PORT, port))));
  }

  @Test
  void testSwitchingTheBuilderToSupplierDropsTheFixedTarget() throws Exception {
    PgConnectOptions first = connectOptions();
    ClientBuilder<Pool> builder =
        PgBuilder.pool()
            .using(vertx)
            .connectingTo(asList(first, new PgConnectOptions(first).setPort(port + 1)))
            .with(new PoolOptions().setMaxSize(1));
    Pool fixedPool = builder.build();
    cleanup.deferCleanup(fixedPool::close);
    Pool supplierPool = builder.connectingTo(() -> Future.succeededFuture(first)).build();
    cleanup.deferCleanup(supplierPool::close);

    select(fixedPool);
    testing.runWithSpan("parent", () -> select(supplierPool));

    testing.waitAndAssertTraces(
        trace -> assertServerListTarget(trace, host + ":" + port + "," + host + ":" + (port + 1)),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
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
                            equalTo(SERVER_PORT, port))));
  }

  private static void assertServerListTarget(TraceAssert trace, String expectedStableAddress) {
    assertServerListTarget(trace, "select * from test", expectedStableAddress);
  }

  private static void assertServerListTarget(
      TraceAssert trace, String statement, String expectedStableAddress) {
    assertServerListTarget(trace, host, port, expectedStableAddress, statement, null);
  }

  private static void assertServerListTarget(
      TraceAssert trace,
      String firstHost,
      int firstPort,
      String expectedStableAddress,
      String statement,
      Throwable error) {
    Consumer<SpanDataAssert> operationSpan =
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
                        emitStableDatabaseSemconv() ? expectedStableAddress : firstHost),
                    equalTo(
                        SERVER_PORT, emitStableDatabaseSemconv() ? null : Long.valueOf(firstPort)),
                    equalTo(
                        ERROR_TYPE,
                        emitStableDatabaseSemconv() && error != null
                            ? "io.netty.channel.AbstractChannel$AnnotatedConnectException"
                            : null));
    if (error == null) {
      trace.hasSpansSatisfyingExactly(operationSpan);
    } else {
      trace.hasSpansSatisfyingExactly(
          operationSpan, span -> span.hasName("CONNECT").hasParent(trace.getSpan(0)));
    }
  }

  private static void assertDirectTarget(TraceAssert trace) {
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
                    equalTo(SERVER_ADDRESS, host),
                    equalTo(SERVER_PORT, port)));
  }

  private static void assertSupplierTarget(TraceAssert trace) {
    assertSupplierTarget(trace, host);
  }

  private static void assertSupplierTarget(TraceAssert trace, String expectedHost) {
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
                    equalTo(SERVER_PORT, port)));
  }

  private static void assertSupplierQuery(
      TraceAssert trace,
      String statement,
      String table,
      String dbSystem,
      String expectedHost,
      Throwable error) {
    trace.hasSpansSatisfyingExactly(
        span -> {
          span.hasName(
                  emitStableDatabaseSemconv()
                      ? "select " + table
                      : "SELECT " + (expectedHost != null ? DB + "." : "") + table)
              .hasKind(SpanKind.CLIENT)
              .hasNoParent()
              .hasStatus(error == null ? StatusData.unset() : StatusData.error())
              .hasAttributesSatisfyingExactly(
                  equalTo(DB_SYSTEM_NAME, emitStableDatabaseSemconv() ? dbSystem : null),
                  equalTo(
                      DB_NAMESPACE,
                      emitStableDatabaseSemconv() && expectedHost != null ? DB : null),
                  equalTo(DB_QUERY_TEXT, emitStableDatabaseSemconv() ? statement : null),
                  equalTo(DB_QUERY_SUMMARY, emitStableDatabaseSemconv() ? "select " + table : null),
                  equalTo(DB_NAME, emitOldDatabaseSemconv() && expectedHost != null ? DB : null),
                  equalTo(
                      DB_USER, emitOldDatabaseSemconv() && expectedHost != null ? USER_DB : null),
                  equalTo(DB_STATEMENT, emitOldDatabaseSemconv() ? statement : null),
                  equalTo(DB_OPERATION, emitOldDatabaseSemconv() ? "SELECT" : null),
                  equalTo(DB_SQL_TABLE, emitOldDatabaseSemconv() ? table : null),
                  equalTo(
                      maybeStablePeerService(),
                      expectedHost != null
                              && (expectedHost.equals(host)
                                  || expectedHost.equals("localhost")
                                  || expectedHost.equals("127.0.0.1"))
                          ? "test-peer-service"
                          : null),
                  equalTo(SERVER_ADDRESS, expectedHost),
                  equalTo(SERVER_PORT, expectedHost != null ? Long.valueOf(port) : null),
                  equalTo(
                      ERROR_TYPE,
                      emitStableDatabaseSemconv() && error != null
                          ? error.getClass().getName()
                          : null));
          if (error == null) {
            span.hasEventsSatisfyingExactly();
          } else {
            span.hasEventsSatisfyingExactly(
                event ->
                    event
                        .hasName("exception")
                        .hasAttributesSatisfyingExactly(
                            equalTo(EXCEPTION_TYPE, error.getClass().getName()),
                            equalTo(EXCEPTION_MESSAGE, error.getMessage()),
                            satisfies(
                                EXCEPTION_STACKTRACE, val -> val.isInstanceOf(String.class))));
          }
        });
  }

  private static CompletableFuture<Throwable> queryOnContext(
      Pool supplierPool, String sql, Context context, AtomicInteger completions) {
    CompletableFuture<Throwable> result = new CompletableFuture<>();
    context.runOnContext(
        ignored -> {
          Thread thread = Thread.currentThread();
          try (Scope ignoredScope =
              io.opentelemetry.context.Context.current().with(REQUEST_KEY, sql).makeCurrent()) {
            supplierPool
                .query(sql)
                .execute()
                .onComplete(
                    queryResult -> {
                      try {
                        completions.incrementAndGet();
                        assertThat(Vertx.currentContext()).isSameAs(context);
                        assertThat(Thread.currentThread()).isSameAs(thread);
                        assertThat(io.opentelemetry.context.Context.current().get(REQUEST_KEY))
                            .isEqualTo(sql);
                        result.complete(queryResult.cause());
                      } catch (Throwable t) {
                        result.completeExceptionally(t);
                      }
                    });
          } catch (Throwable t) {
            result.completeExceptionally(t);
          }
        });
    return result;
  }

  private static void assertSupplierFailure(TraceAssert trace, RuntimeException error) {
    assertSupplierFailure(trace, error, POSTGRESQL, null);
  }

  private static void assertSupplierFailure(
      TraceAssert trace, RuntimeException error, String dbSystem) {
    assertSupplierFailure(trace, error, dbSystem, null);
  }

  private static void assertSupplierFailure(
      TraceAssert trace, RuntimeException error, String dbSystem, String expectedHost) {
    trace.hasSpansSatisfyingExactly(
        span ->
            span.hasName(
                    emitStableDatabaseSemconv()
                        ? "select test"
                        : expectedHost != null ? "SELECT tempdb.test" : "SELECT test")
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
                    equalTo(maybeStable(DB_SYSTEM), emitStableDatabaseSemconv() ? dbSystem : null),
                    equalTo(maybeStable(DB_NAME), expectedHost != null ? DB : null),
                    equalTo(
                        DB_USER,
                        expectedHost != null && !emitStableDatabaseSemconv() ? USER_DB : null),
                    equalTo(maybeStable(DB_STATEMENT), "select * from test"),
                    equalTo(DB_QUERY_SUMMARY, emitStableDatabaseSemconv() ? "select test" : null),
                    equalTo(
                        maybeStable(DB_OPERATION), emitStableDatabaseSemconv() ? null : "SELECT"),
                    equalTo(maybeStable(DB_SQL_TABLE), emitStableDatabaseSemconv() ? null : "test"),
                    equalTo(
                        maybeStablePeerService(),
                        expectedHost != null && expectedHost.equals(host)
                            ? "test-peer-service"
                            : null),
                    equalTo(SERVER_ADDRESS, expectedHost),
                    equalTo(SERVER_PORT, expectedHost != null ? Long.valueOf(port) : null),
                    equalTo(
                        ERROR_TYPE,
                        emitStableDatabaseSemconv() ? error.getClass().getName() : null)));
  }

  private static void assertSupplierFallbackFailure(TraceAssert trace, RuntimeException error) {
    trace.hasSpansSatisfyingExactly(
        span ->
            span.hasName("")
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
                        maybeStable(DB_SYSTEM), emitStableDatabaseSemconv() ? "other_sql" : null),
                    equalTo(maybeStable(DB_NAME), ""),
                    equalTo(DB_USER, emitStableDatabaseSemconv() ? null : ""),
                    equalTo(maybeStable(DB_STATEMENT), ""),
                    equalTo(DB_QUERY_SUMMARY, null),
                    equalTo(maybeStable(DB_OPERATION), null),
                    equalTo(maybeStable(DB_SQL_TABLE), null),
                    equalTo(maybeStablePeerService(), "test-peer-service"),
                    equalTo(SERVER_ADDRESS, host),
                    equalTo(SERVER_PORT, port),
                    equalTo(
                        ERROR_TYPE,
                        emitStableDatabaseSemconv() ? error.getClass().getName() : null)));
  }

  private static PgDriver failingDriver(RuntimeException failure) {
    return driver(options -> Future.failedFuture(failure));
  }

  private static PgDriver driver(Function<PgConnectOptions, Future<?>> connectionProvider) {
    return TestPgDriver.create(connectionProvider);
  }

  private static void assertOracleConnectFailure(TraceAssert trace, Throwable error) {
    trace.hasSpansSatisfyingExactly(
        span ->
            span.hasName(emitStableDatabaseSemconv() ? "select test" : "SELECT testdb.test")
                .hasKind(SpanKind.CLIENT)
                .hasStatus(StatusData.error())
                .hasEventsSatisfyingExactly(
                    event ->
                        event
                            .hasName("exception")
                            .hasAttributesSatisfyingExactly(
                                equalTo(EXCEPTION_TYPE, error.getClass().getName()),
                                satisfies(EXCEPTION_MESSAGE, val -> val.isNotBlank()),
                                satisfies(
                                    EXCEPTION_STACKTRACE, val -> val.isInstanceOf(String.class))))
                .hasAttributesSatisfyingExactly(
                    equalTo(
                        maybeStable(DB_SYSTEM), emitStableDatabaseSemconv() ? "oracle.db" : null),
                    equalTo(maybeStable(DB_NAME), "testdb"),
                    equalTo(DB_USER, emitStableDatabaseSemconv() ? null : "testuser"),
                    equalTo(maybeStable(DB_STATEMENT), "select * from test"),
                    equalTo(DB_QUERY_SUMMARY, emitStableDatabaseSemconv() ? "select test" : null),
                    equalTo(
                        maybeStable(DB_OPERATION), emitStableDatabaseSemconv() ? null : "SELECT"),
                    equalTo(maybeStable(DB_SQL_TABLE), emitStableDatabaseSemconv() ? null : "test"),
                    equalTo(maybeStablePeerService(), "test-peer-service"),
                    equalTo(SERVER_ADDRESS, "127.0.0.1"),
                    equalTo(SERVER_PORT, 1),
                    equalTo(ERROR_TYPE, emitStableDatabaseSemconv() ? "66000" : null)));
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
