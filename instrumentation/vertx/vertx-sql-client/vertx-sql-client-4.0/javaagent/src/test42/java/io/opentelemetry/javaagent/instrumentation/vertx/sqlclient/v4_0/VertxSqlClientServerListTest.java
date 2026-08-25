/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_USER;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.POSTGRESQL;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

/** Pools over a list of servers were added in vert.x 4.2, the version this suite runs against. */
@SuppressWarnings("deprecation") // using deprecated semconv
class VertxSqlClientServerListTest {

  private static final Logger logger = LoggerFactory.getLogger(VertxSqlClientServerListTest.class);

  private static final String USER_DB = "SA";
  private static final String PW_DB = "password123";
  private static final String DB = "tempdb";

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static Vertx vertx;
  private static String host;
  private static int port;

  @BeforeAll
  static void setUp() {
    GenericContainer<?> container =
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
  }

  @Test
  void serverListIsReportedAsOneTarget()
      throws InterruptedException, ExecutionException, TimeoutException {
    // the first server is the running database, the second one only makes the target a group
    PgConnectOptions first = connectOptions().setPort(port);
    PgConnectOptions second = connectOptions().setPort(port + 1);
    Pool pool = PgPool.pool(vertx, asList(first, second), poolOptions());
    cleanup.deferCleanup(pool::close);

    select(pool);

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
                            equalTo(maybeStable(DB_STATEMENT), "select ?"),
                            equalTo(
                                DB_QUERY_SUMMARY, emitStableDatabaseSemconv() ? "select" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"),
                            equalTo(
                                maybeStablePeerService(),
                                emitStableDatabaseSemconv() ? null : "test-peer-service"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? host + ":" + port + "," + host + ":" + (port + 1)
                                    : host),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv() ? null : Long.valueOf(port)))));
  }

  @Test
  void oneServerIsReportedAsHostAndPort()
      throws InterruptedException, ExecutionException, TimeoutException {
    Pool pool = PgPool.pool(vertx, singletonList(connectOptions().setPort(port)), poolOptions());
    cleanup.deferCleanup(pool::close);

    select(pool);

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
                            equalTo(maybeStable(DB_STATEMENT), "select ?"),
                            equalTo(
                                DB_QUERY_SUMMARY, emitStableDatabaseSemconv() ? "select" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"),
                            equalTo(maybeStablePeerService(), "test-peer-service"),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, Long.valueOf(port)))));
  }

  @Test
  void singlePoolBuiltBeforeListPoolKeepsItsOwnTarget()
      throws InterruptedException, ExecutionException, TimeoutException {
    PgConnectOptions primary = connectOptions().setPort(port);
    Pool singlePool = PgPool.pool(vertx, primary, poolOptions());
    Pool listPool =
        PgPool.pool(vertx, asList(primary, connectOptions().setPort(port + 1)), poolOptions());

    assertEachPoolReportsItsOwnTarget(singlePool, listPool);
  }

  @Test
  void singlePoolBuiltAfterListPoolKeepsItsOwnTarget()
      throws InterruptedException, ExecutionException, TimeoutException {
    PgConnectOptions primary = connectOptions().setPort(port);
    Pool listPool =
        PgPool.pool(vertx, asList(primary, connectOptions().setPort(port + 1)), poolOptions());
    Pool singlePool = PgPool.pool(vertx, primary, poolOptions());

    assertEachPoolReportsItsOwnTarget(singlePool, listPool);
  }

  /**
   * Both pools are built from the same {@link PgConnectOptions} instance, which the caller owns and
   * may hand to any number of clients.
   */
  private static void assertEachPoolReportsItsOwnTarget(Pool singlePool, Pool listPool)
      throws InterruptedException, ExecutionException, TimeoutException {
    cleanup.deferCleanup(singlePool::close);
    cleanup.deferCleanup(listPool::close);

    select(singlePool);
    select(listPool);

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
                            equalTo(maybeStable(DB_STATEMENT), "select ?"),
                            equalTo(
                                DB_QUERY_SUMMARY, emitStableDatabaseSemconv() ? "select" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"),
                            equalTo(maybeStablePeerService(), "test-peer-service"),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, Long.valueOf(port)))),
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
                            equalTo(maybeStable(DB_STATEMENT), "select ?"),
                            equalTo(
                                DB_QUERY_SUMMARY, emitStableDatabaseSemconv() ? "select" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"),
                            equalTo(
                                maybeStablePeerService(),
                                emitStableDatabaseSemconv() ? null : "test-peer-service"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? host + ":" + port + "," + host + ":" + (port + 1)
                                    : host),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv() ? null : Long.valueOf(port)))));
  }

  private static void select(Pool pool)
      throws InterruptedException, ExecutionException, TimeoutException {
    pool.query("select 1").execute().toCompletionStage().toCompletableFuture().get(30, SECONDS);
  }

  private static PoolOptions poolOptions() {
    return new PoolOptions().setMaxSize(1);
  }

  private static PgConnectOptions connectOptions() {
    return new PgConnectOptions().setHost(host).setDatabase(DB).setUser(USER_DB).setPassword(PW_DB);
  }
}
