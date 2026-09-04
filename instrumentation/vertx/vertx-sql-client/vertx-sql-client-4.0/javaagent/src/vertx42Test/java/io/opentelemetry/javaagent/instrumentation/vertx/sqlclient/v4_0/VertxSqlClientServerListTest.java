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
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;
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
  void serverListWithUnixSocketOmitsStableTarget() throws Exception {
    PgConnectOptions first = connectOptions().setPort(port);
    PgConnectOptions second = connectOptions().setHost("/var/run/postgres:primary").setPort(5432);
    Pool pool = PgPool.pool(vertx, asList(first, second), poolOptions());

    assertServerListTarget(pool, null);
  }

  @Test
  void serverListWithIpv6LiteralIsReportedAsOneTarget() throws Exception {
    PgConnectOptions first = connectOptions().setPort(port);
    PgConnectOptions second = connectOptions().setHost("2001:db8::1").setPort(5432);
    Pool pool = PgPool.pool(vertx, asList(first, second), poolOptions());

    assertServerListTarget(pool, host + ":" + port + ",[2001:db8::1]:5432");
  }

  @Test
  void clientServerListIsReportedAsOneTarget() throws Exception {
    PgConnectOptions first = connectOptions().setPort(port);
    PgConnectOptions second = connectOptions().setPort(port + 1);
    SqlClient client = PgPool.client(vertx, asList(first, second), poolOptions());

    assertServerListTarget(client, host + ":" + port + "," + host + ":" + (port + 1));
  }

  @Test
  void serverListReportsCompleteLongEndpoint() throws Exception {
    PgConnectOptions first = connectOptions().setPort(port);
    String secondHost = hostOfLength(250);
    PgConnectOptions second = connectOptions().setHost(secondHost).setPort(port + 1);
    Pool pool = PgPool.pool(vertx, asList(first, second), poolOptions());

    assertServerListTarget(pool, host + ":" + port + "," + secondHost + ":" + (port + 1));
  }

  @Test
  void preparedStatementServerListIsReportedAsOneTarget() throws Exception {
    PgConnectOptions first = connectOptions().setPort(port);
    PgConnectOptions second = connectOptions().setPort(port + 1);
    Pool pool = PgPool.pool(vertx, asList(first, second), poolOptions());
    cleanup.deferCleanup(pool::close);
    String query = "select cast($1 as integer)";

    executePreparedStatement(pool, query, Tuple.of(1));

    assertServerListTarget(host + ":" + port + "," + host + ":" + (port + 1), query);
  }

  @Test
  void differingDatabaseAndUserAreOmitted() throws Exception {
    PgConnectOptions first = connectOptions().setPort(port);
    PgConnectOptions second =
        connectOptions().setPort(port + 1).setDatabase("other-database").setUser("other-user");
    Pool pool = PgPool.pool(vertx, asList(first, second), poolOptions());

    assertServerListTarget(pool, host + ":" + port + "," + host + ":" + (port + 1), null, null);
  }

  @Test
  void nullServerDoesNotPoisonLaterPoolTarget() throws Exception {
    PgConnectOptions first = connectOptions().setPort(port);
    try {
      Pool malformedPool = PgPool.pool(vertx, asList(first, null), poolOptions());
      cleanup.deferCleanup(malformedPool::close);
    } catch (RuntimeException ignored) {
      // Vert.x may reject malformed server lists.
    }

    PgConnectOptions second = connectOptions().setPort(port + 1);
    Pool pool = PgPool.pool(vertx, asList(first, second), poolOptions());

    assertServerListTarget(pool, host + ":" + port + "," + host + ":" + (port + 1));
  }

  private static void assertServerListTarget(SqlClient client, String serverAddress)
      throws InterruptedException, ExecutionException, TimeoutException {
    assertServerListTarget(client, serverAddress, DB, USER_DB);
  }

  private static void assertServerListTarget(
      SqlClient client, String serverAddress, String database, String user)
      throws InterruptedException, ExecutionException, TimeoutException {
    cleanup.deferCleanup(client::close);
    select(client);
    assertServerListTarget(serverAddress, "select ?", database, user);
  }

  private static void assertServerListTarget(String serverAddress, String statement) {
    assertServerListTarget(serverAddress, statement, DB, USER_DB);
  }

  private static void assertServerListTarget(
      String serverAddress, String statement, String database, String user) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                maybeStable(DB_SYSTEM),
                                emitStableDatabaseSemconv() ? POSTGRESQL : null),
                            equalTo(maybeStable(DB_NAME), database),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : user),
                            equalTo(maybeStable(DB_STATEMENT), statement),
                            equalTo(
                                DB_QUERY_SUMMARY, emitStableDatabaseSemconv() ? "select" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"),
                            equalTo(
                                maybeStablePeerService(),
                                emitStableDatabaseSemconv() ? null : "test-peer-service"),
                            equalTo(
                                SERVER_ADDRESS, emitStableDatabaseSemconv() ? serverAddress : host),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv() ? null : Long.valueOf(port)))));
  }

  @Test
  void oneServerIsReportedAsHostAndPort() throws Exception {
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
                            equalTo(SERVER_PORT, port))));
  }

  @Test
  void singlePoolBuiltBeforeListPoolKeepsItsOwnTarget() throws Exception {
    PgConnectOptions primary = connectOptions().setPort(port);
    Pool singlePool = PgPool.pool(vertx, primary, poolOptions());
    Pool listPool =
        PgPool.pool(vertx, asList(primary, connectOptions().setPort(port + 1)), poolOptions());

    assertEachPoolReportsItsOwnTarget(singlePool, listPool);
  }

  @Test
  void singlePoolBuiltAfterListPoolKeepsItsOwnTarget() throws Exception {
    PgConnectOptions primary = connectOptions().setPort(port);
    Pool listPool =
        PgPool.pool(vertx, asList(primary, connectOptions().setPort(port + 1)), poolOptions());
    Pool singlePool = PgPool.pool(vertx, primary, poolOptions());

    assertEachPoolReportsItsOwnTarget(singlePool, listPool);
  }

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
                            equalTo(SERVER_PORT, port))),
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

  private static void select(SqlClient client)
      throws InterruptedException, ExecutionException, TimeoutException {
    client.query("select 1").execute().toCompletionStage().toCompletableFuture().get(30, SECONDS);
  }

  private static void executePreparedStatement(Pool pool, String query, Tuple tuple)
      throws InterruptedException, ExecutionException, TimeoutException {
    pool.withConnection(
            connection ->
                connection
                    .prepare(query)
                    .compose(
                        statement ->
                            statement
                                .query()
                                .execute(tuple)
                                .compose(
                                    rows -> statement.close().map(rows),
                                    error ->
                                        statement
                                            .close()
                                            .compose(ignored -> Future.failedFuture(error)))))
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, SECONDS);
  }

  private static PoolOptions poolOptions() {
    return new PoolOptions().setMaxSize(1);
  }

  private static PgConnectOptions connectOptions() {
    return new PgConnectOptions().setHost(host).setDatabase(DB).setUser(USER_DB).setPassword(PW_DB);
  }

  private static String hostOfLength(int length) {
    StringBuilder host = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      host.append(i > 0 && i % 64 == 63 ? '.' : 'a');
    }
    return host.toString();
  }
}
