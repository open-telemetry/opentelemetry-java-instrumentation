/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

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
    testing
        .runWithSpan(
            "parent",
            () -> pool.preparedQuery("select * from test where id = $1").execute(Tuple.of(1)))
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, SECONDS);

    assertPreparedSelect();
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

  // describes the batch cases: a single-statement batch (not a batch -> no db.operation.batch.size)
  // and two statements. batch telemetry (db.operation.batch.size, BATCH span names and summaries)
  // is only emitted under stable database semconv
  @ParameterizedTest
  @MethodSource("batchScenarios")
  void testBatch(BatchScenario scenario) throws Exception {
    // an empty batch is rejected before sending, so its execution fails; non-empty batches succeed
    try {
      testing
          .runWithSpan(
              "parent",
              () ->
                  pool.preparedQuery("insert into test values ($1, $2) returning *")
                      .executeBatch(scenario.tuples))
          .toCompletionStage()
          .toCompletableFuture()
          .get(30, SECONDS);
    } catch (ExecutionException e) {
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
                                : "INSERT tempdb.test")
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
                                "insert into test values ($1, $2) returning *"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? scenario.stableSummary : null),
                            equalTo(
                                DB_OPERATION_BATCH_SIZE,
                                emitStableDatabaseSemconv() ? scenario.batchSize : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "INSERT"),
                            equalTo(
                                maybeStable(DB_SQL_TABLE),
                                emitStableDatabaseSemconv() ? null : "test"),
                            equalTo(
                                ERROR_TYPE,
                                emitStableDatabaseSemconv() ? scenario.errorType : null),
                            equalTo(maybeStablePeerService(), "test-peer-service"),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port))));
  }

  private static Stream<Arguments> batchScenarios() {
    return Stream.of(
            // an empty batch is rejected before sending, so it looks like a single statement but
            // records the error and emits no db.operation.batch.size
            BatchScenario.builder("empty")
                .tuples(emptyList())
                .stableSpanName("insert test")
                .stableSummary("insert test")
                .errorType("io.vertx.core.VertxException")
                .build(),
            // a single-statement batch is not a batch (size 1), so it emits no
            // db.operation.batch.size and no BATCH prefix
            BatchScenario.builder("single")
                .tuples(singletonList(Tuple.of(3, "Three")))
                .stableSpanName("insert test")
                .stableSummary("insert test")
                .build(),
            BatchScenario.builder("twoSameOperation")
                .tuples(asList(Tuple.of(4, "Four"), Tuple.of(5, "Five")))
                .stableSpanName("BATCH insert test")
                .stableSummary("BATCH insert test")
                .batchSize(2)
                .build())
        .map(Arguments::of);
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
    final String name;
    final List<Tuple> tuples;
    final String stableSpanName;
    final String stableSummary;
    final Long batchSize;
    final String errorType;

    BatchScenario(Builder builder) {
      this.name = builder.name;
      this.tuples = builder.tuples;
      this.stableSpanName = builder.stableSpanName;
      this.stableSummary = builder.stableSummary;
      this.batchSize = builder.batchSize;
      this.errorType = builder.errorType;
    }

    static Builder builder(String name) {
      return new Builder(name);
    }

    @Override
    public String toString() {
      // used as the parameterized test display name
      return name;
    }

    static final class Builder {
      private final String name;
      private List<Tuple> tuples;
      private String stableSpanName;
      private String stableSummary;
      private Long batchSize;
      private String errorType;

      Builder(String name) {
        this.name = name;
      }

      Builder tuples(List<Tuple> tuples) {
        this.tuples = tuples;
        return this;
      }

      Builder stableSpanName(String stableSpanName) {
        this.stableSpanName = stableSpanName;
        return this;
      }

      Builder stableSummary(String stableSummary) {
        this.stableSummary = stableSummary;
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
