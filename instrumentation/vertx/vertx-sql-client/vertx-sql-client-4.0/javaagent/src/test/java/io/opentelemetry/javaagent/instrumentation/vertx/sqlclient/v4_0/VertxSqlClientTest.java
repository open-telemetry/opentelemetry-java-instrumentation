/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
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
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgException;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
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
import java.util.function.Consumer;
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
  void testSimpleSelect() throws Exception {
    CompletableFuture<Object> future = new CompletableFuture<>();
    CompletableFuture<Object> result =
        future.whenComplete((rows, throwable) -> testing.runWithSpan("callback", () -> {}));
    testing.runWithSpan(
        "parent",
        () ->
            pool.query("select * from test")
                .execute(
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
  }

  @Test
  void testInvalidQuery() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    CompletableFuture<Object> result = new CompletableFuture<>();
    result.whenComplete(
        (rows, throwable) -> testing.runWithSpan("callback", () -> latch.countDown()));
    testing.runWithSpan(
        "parent",
        () ->
            pool.query("invalid")
                .execute(
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
    testing
        .runWithSpan(
            "parent",
            () -> pool.preparedQuery("select * from test where id = $1").execute(Tuple.of(1)))
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, SECONDS);

    assertPreparedSelect();

    assertDurationMetric(
        testing,
        "io.opentelemetry.vertx-sql-client-4.0",
        DB_SYSTEM_NAME,
        DB_NAMESPACE,
        DB_QUERY_SUMMARY,
        SERVER_ADDRESS,
        SERVER_PORT);
  }

  private static void assertPreparedSelect() {
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
                            equalTo(maybeStable(DB_STATEMENT), "select * from test where id = $1"),
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
                            equalTo(maybeStable(DB_STATEMENT), scenario.preparedQuery),
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
        Arguments.argumentSet(
            "empty",
            BatchScenario.builder()
                .preparedQuery("insert into batch_test values ($1, $2) returning *")
                .tuples(emptyList())
                .stableSpanName("insert batch_test")
                .querySummary("insert batch_test")
                .errorType("io.vertx.core.impl.NoStackTraceThrowable")
                .build()),
        Arguments.argumentSet(
            "single",
            BatchScenario.builder()
                .preparedQuery("insert into batch_test values ($1, $2) returning *")
                .tuples(singletonList(Tuple.of(1, 1)))
                .stableSpanName("insert batch_test")
                .querySummary("insert batch_test")
                .build()),
        Arguments.argumentSet(
            "twoSameOperation",
            BatchScenario.builder()
                .preparedQuery("insert into batch_test values ($1, $2) returning *")
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
                  .execute(
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
    final List<Tuple> tuples;
    final String stableSpanName;
    final String querySummary;
    final Long batchSize;
    final String errorType;

    BatchScenario(Builder builder) {
      this.preparedQuery = builder.preparedQuery;
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
      private List<Tuple> tuples;
      private String stableSpanName;
      private String querySummary;
      private Long batchSize;
      private String errorType;

      Builder preparedQuery(String preparedQuery) {
        this.preparedQuery = preparedQuery;
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
