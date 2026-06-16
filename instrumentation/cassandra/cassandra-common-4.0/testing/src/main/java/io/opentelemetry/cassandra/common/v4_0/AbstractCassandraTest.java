/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.cassandra.common.v4_0;

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
import static org.junit.jupiter.api.Named.named;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.DefaultBatchType;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultDriverConfigLoader;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

@SuppressWarnings("deprecation") // using deprecated semconv
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractCassandraTest {

  private static final Logger logger = LoggerFactory.getLogger(AbstractCassandraTest.class);

  @RegisterExtension protected final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private GenericContainer<?> cassandra;

  protected String cassandraHost;
  protected String cassandraIp;
  protected int cassandraPort;

  protected abstract InstrumentationExtension testing();

  protected abstract String getInstrumentationName();

  protected CqlSession wrap(CqlSession session) {
    return session;
  }

  @BeforeAll
  void beforeAll() throws UnknownHostException {
    cassandra =
        new GenericContainer<>("cassandra:4.0")
            .withEnv("JVM_OPTS", "-Xmx128m -Xms128m")
            .withExposedPorts(9042)
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .withStartupTimeout(Duration.ofMinutes(2));
    cleanup.deferAfterAll(cassandra::stop);
    cassandra.start();

    cassandraHost = cassandra.getHost();
    cassandraIp = InetAddress.getByName(cassandra.getHost()).getHostAddress();
    cassandraPort = cassandra.getMappedPort(9042);
  }

  @Test
  void testMetrics() {
    CqlSession session = getSession(null);
    cleanup.deferCleanup(session);

    session.execute("DROP KEYSPACE IF EXISTS metrics_test");
    session.execute(
        "CREATE KEYSPACE metrics_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':1}");
    testing().clearData();

    session.execute("CREATE TABLE metrics_test.users ( id UUID PRIMARY KEY, name text )");

    assertDurationMetric(
        testing(),
        getInstrumentationName(),
        DB_SYSTEM_NAME,
        DB_OPERATION_NAME,
        DB_COLLECTION_NAME,
        DB_QUERY_SUMMARY,
        NETWORK_PEER_ADDRESS,
        NETWORK_PEER_PORT,
        SERVER_ADDRESS,
        SERVER_PORT);
  }

  @Test
  void simpleStatementWithValues() {
    CqlSession session = getSession(null);
    cleanup.deferCleanup(session);

    session.execute("DROP KEYSPACE IF EXISTS simple_values_test");
    session.execute(
        "CREATE KEYSPACE simple_values_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':1}");
    session.execute("CREATE TABLE simple_values_test.users ( name text PRIMARY KEY, age int )");
    testing().clearData();

    session.execute(
        SimpleStatement.newInstance(
            "INSERT INTO simple_values_test.users (name, age) values ('alice', ?)", 1));

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("INSERT simple_values_test.users")
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                satisfies(
                                    NETWORK_TYPE,
                                    emitStableDatabaseSemconv()
                                        ? val -> val.isNull()
                                        : val -> val.isIn("ipv4", "ipv6")),
                                equalTo(SERVER_ADDRESS, cassandraHost),
                                equalTo(SERVER_PORT, cassandraPort),
                                equalTo(NETWORK_PEER_ADDRESS, cassandraIp),
                                equalTo(NETWORK_PEER_PORT, cassandraPort),
                                equalTo(maybeStable(DB_SYSTEM), CASSANDRA),
                                equalTo(
                                    maybeStable(DB_STATEMENT),
                                    emitStableDatabaseSemconv()
                                        ? "INSERT INTO simple_values_test.users (name, age) values ('alice', ?)"
                                        : "INSERT INTO simple_values_test.users (name, age) values (?, ?)"),
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
                                    val -> val.isInstanceOf(String.class)),
                                satisfies(
                                    maybeStable(DB_CASSANDRA_IDEMPOTENCE),
                                    val -> val.isInstanceOf(Boolean.class)),
                                equalTo(maybeStable(DB_CASSANDRA_PAGE_SIZE), 5000),
                                equalTo(maybeStable(DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT), 0),
                                equalTo(
                                    maybeStable(DB_CASSANDRA_TABLE), "simple_values_test.users"))));
  }

  // describes the batch cases: a single-statement batch (which is executed as a normal statement,
  // not a batch), two statements with the same query, and two statements with different queries.
  // (an
  // empty batch is invalid CQL.) batch telemetry (db.operation.batch.size, BATCH span names and
  // summaries) is only emitted under stable database semconv
  @ParameterizedTest
  @MethodSource("batchScenarios")
  void batchStatement(BatchScenario scenario) {
    CqlSession session = getSession(null);
    cleanup.deferCleanup(session);

    session.execute("DROP KEYSPACE IF EXISTS " + scenario.keyspace);
    session.execute(
        "CREATE KEYSPACE "
            + scenario.keyspace
            + " WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':1}");
    session.execute(
        "CREATE TABLE " + scenario.keyspace + ".users ( name text PRIMARY KEY, age int )");
    testing().waitForTraces(3);
    testing().clearData();

    session.execute(scenario.buildBatch.apply(session));

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(
                                emitStableDatabaseSemconv()
                                    ? scenario.spanName
                                    : scenario.oldSpanName)
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                satisfies(
                                    NETWORK_TYPE,
                                    emitStableDatabaseSemconv()
                                        ? val -> val.isNull()
                                        : val -> val.isIn("ipv4", "ipv6")),
                                equalTo(SERVER_ADDRESS, cassandraHost),
                                equalTo(SERVER_PORT, cassandraPort),
                                equalTo(NETWORK_PEER_ADDRESS, cassandraIp),
                                equalTo(NETWORK_PEER_PORT, cassandraPort),
                                equalTo(maybeStable(DB_SYSTEM), CASSANDRA),
                                equalTo(
                                    maybeStable(DB_STATEMENT),
                                    emitStableDatabaseSemconv()
                                        ? scenario.statement
                                        : scenario.oldStatement),
                                equalTo(
                                    DB_OPERATION_BATCH_SIZE,
                                    emitStableDatabaseSemconv() ? scenario.batchSize : null),
                                equalTo(
                                    DB_QUERY_SUMMARY,
                                    emitStableDatabaseSemconv() ? scenario.summary : null),
                                // under stable semconv a batch carries db.operation.name (BATCH,
                                // or BATCH <operation> when all statements share one operation) and
                                // db.collection.name (when all statements share one collection); a
                                // single-statement batch is not a batch, so it carries the normal
                                // statement's db.operation.name and db.cassandra.table
                                equalTo(
                                    maybeStable(DB_OPERATION),
                                    emitStableDatabaseSemconv()
                                        ? scenario.operation
                                        : scenario.oldOperation),
                                equalTo(
                                    maybeStable(DB_CASSANDRA_TABLE),
                                    emitStableDatabaseSemconv()
                                        ? scenario.collection
                                        : scenario.oldCollection),
                                equalTo(maybeStable(DB_CASSANDRA_CONSISTENCY_LEVEL), "LOCAL_ONE"),
                                equalTo(maybeStable(DB_CASSANDRA_COORDINATOR_DC), "datacenter1"),
                                satisfies(
                                    maybeStable(DB_CASSANDRA_COORDINATOR_ID),
                                    val -> val.isInstanceOf(String.class)),
                                satisfies(
                                    maybeStable(DB_CASSANDRA_IDEMPOTENCE),
                                    val -> val.isInstanceOf(Boolean.class)),
                                equalTo(maybeStable(DB_CASSANDRA_PAGE_SIZE), 5000),
                                equalTo(
                                    maybeStable(DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT), 0))));
  }

  private static Stream<Arguments> batchScenarios() {
    return Stream.of(
            // an empty batch still produces a client span, but with no query text, summary,
            // operation or batch size; the span name falls back to the database system name
            BatchScenario.builder("empty")
                .keyspace("batch_empty_test")
                .buildBatch(session -> BatchStatement.newInstance(DefaultBatchType.LOGGED))
                .spanName("cassandra")
                .oldSpanName("DB Query")
                .build(),
            // a single-statement batch is executed as a normal statement (not a batch): it has the
            // normal INSERT span name in both modes, db.operation and db.cassandra.table, and no
            // db.operation.batch.size
            BatchScenario.builder("single")
                .keyspace("batch_single_test")
                .buildBatch(
                    session -> {
                      PreparedStatement insert =
                          session.prepare(
                              "INSERT INTO batch_single_test.users (name, age) values (?, ?)");
                      return BatchStatement.newInstance(
                          DefaultBatchType.LOGGED, insert.bind("alice", 1));
                    })
                .spanName("INSERT batch_single_test.users")
                .oldSpanName("INSERT batch_single_test.users")
                .statement("INSERT INTO batch_single_test.users (name, age) values (?, ?)")
                .oldStatement("INSERT INTO batch_single_test.users (name, age) values (?, ?)")
                .summary("INSERT batch_single_test.users")
                .operation("INSERT")
                .oldOperation("INSERT")
                .collection("batch_single_test.users")
                .oldCollection("batch_single_test.users")
                .build(),
            BatchScenario.builder("twoSameOperation")
                .keyspace("batch_same_test")
                .buildBatch(
                    session -> {
                      PreparedStatement insert =
                          session.prepare(
                              "INSERT INTO batch_same_test.users (name, age) values (?, ?)");
                      return BatchStatement.newInstance(
                          DefaultBatchType.LOGGED, insert.bind("alice", 1), insert.bind("bob", 2));
                    })
                .spanName("BATCH INSERT batch_same_test.users")
                .oldSpanName("DB Query")
                .statement("INSERT INTO batch_same_test.users (name, age) values (?, ?)")
                .summary("BATCH INSERT batch_same_test.users")
                .operation("BATCH INSERT batch_same_test.users")
                .collection("batch_same_test.users")
                .batchSize(2)
                .build(),
            BatchScenario.builder("twoDifferentOperations")
                .keyspace("batch_mixed_test")
                .buildBatch(
                    session -> {
                      PreparedStatement insert =
                          session.prepare(
                              "INSERT INTO batch_mixed_test.users (name, age) values ('alice', ?)");
                      return BatchStatement.newInstance(
                          DefaultBatchType.LOGGED,
                          insert.bind(1),
                          SimpleStatement.newInstance(
                              "UPDATE batch_mixed_test.users SET age = 2 WHERE name = 'alice'"));
                    })
                .spanName("BATCH")
                .oldSpanName("DB Query")
                .statement(
                    "INSERT INTO batch_mixed_test.users (name, age) values ('alice', ?); UPDATE batch_mixed_test.users SET age = ? WHERE name = ?")
                .summary("BATCH")
                .operation("BATCH")
                .collection("batch_mixed_test.users")
                .batchSize(2)
                .build())
        .map(Arguments::of);
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("provideSyncParameters")
  void syncTest(Parameter parameter) {
    CqlSession session = getSession(parameter.keyspace);
    cleanup.deferCleanup(session);

    session.execute(parameter.queryText);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(parameter.spanName)
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                satisfies(
                                    NETWORK_TYPE,
                                    emitStableDatabaseSemconv()
                                        ? val -> val.isNull()
                                        : val -> val.isIn("ipv4", "ipv6")),
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
                                    val -> val.isInstanceOf(String.class)),
                                satisfies(
                                    maybeStable(DB_CASSANDRA_IDEMPOTENCE),
                                    val -> val.isInstanceOf(Boolean.class)),
                                equalTo(maybeStable(DB_CASSANDRA_PAGE_SIZE), 5000),
                                equalTo(maybeStable(DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT), 0),
                                equalTo(maybeStable(DB_CASSANDRA_TABLE), parameter.table))));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("provideAsyncParameters")
  void asyncTest(Parameter parameter) {
    CqlSession session = getSession(parameter.keyspace);
    cleanup.deferCleanup(session);

    testing()
        .runWithSpan(
            "parent",
            () ->
                session
                    .executeAsync(parameter.queryText)
                    .toCompletableFuture()
                    .whenComplete((result, throwable) -> testing().runWithSpan("child", () -> {}))
                    .join());

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(parameter.spanName)
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                satisfies(
                                    NETWORK_TYPE,
                                    emitStableDatabaseSemconv()
                                        ? val -> val.isNull()
                                        : val -> val.isIn("ipv4", "ipv6")),
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
                                    val -> val.isInstanceOf(String.class)),
                                satisfies(
                                    maybeStable(DB_CASSANDRA_IDEMPOTENCE),
                                    val -> val.isInstanceOf(Boolean.class)),
                                equalTo(maybeStable(DB_CASSANDRA_PAGE_SIZE), 5000),
                                equalTo(maybeStable(DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT), 0),
                                equalTo(maybeStable(DB_CASSANDRA_TABLE), parameter.table)),
                    span ->
                        span.hasName("child")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
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

  protected static class Parameter {
    @Nullable public final String keyspace;
    public final String queryText;
    public final String expectedQueryText;
    public final String spanName;
    public final String operation;
    @Nullable public final String table;

    public Parameter(
        @Nullable String keyspace,
        String queryText,
        String expectedQueryText,
        String spanName,
        String operation,
        @Nullable String table) {
      this.keyspace = keyspace;
      this.queryText = queryText;
      this.expectedQueryText = expectedQueryText;
      this.spanName = spanName;
      this.operation = operation;
      this.table = table;
    }
  }

  protected CqlSession getSession(@Nullable String keyspace) {
    DriverConfigLoader configLoader =
        DefaultDriverConfigLoader.builder()
            .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(0))
            .withDuration(DefaultDriverOption.CONNECTION_INIT_QUERY_TIMEOUT, Duration.ofSeconds(10))
            .build();
    return wrap(
        addContactPoint(CqlSession.builder())
            .withConfigLoader(configLoader)
            .withLocalDatacenter("datacenter1")
            .withKeyspace(keyspace)
            .build());
  }

  protected CqlSessionBuilder addContactPoint(CqlSessionBuilder sessionBuilder) {
    sessionBuilder.addContactPoint(new InetSocketAddress(cassandra.getHost(), cassandraPort));
    return sessionBuilder;
  }

  private static final class BatchScenario {
    final String name;
    final String keyspace;
    final Function<CqlSession, BatchStatement> buildBatch;
    final String spanName;
    final String oldSpanName;
    final String statement;
    final String oldStatement;
    final String summary;
    final Long batchSize;
    final String operation;
    final String oldOperation;
    final String collection;
    final String oldCollection;

    BatchScenario(Builder builder) {
      this.name = builder.name;
      this.keyspace = builder.keyspace;
      this.buildBatch = builder.buildBatch;
      this.spanName = builder.spanName;
      this.oldSpanName = builder.oldSpanName;
      this.statement = builder.statement;
      this.oldStatement = builder.oldStatement;
      this.summary = builder.summary;
      this.batchSize = builder.batchSize;
      this.operation = builder.operation;
      this.oldOperation = builder.oldOperation;
      this.collection = builder.collection;
      this.oldCollection = builder.oldCollection;
    }

    @Override
    public String toString() {
      // used as the parameterized test display name
      return name;
    }

    static Builder builder(String name) {
      return new Builder(name);
    }

    static final class Builder {
      private final String name;
      private String keyspace;
      private Function<CqlSession, BatchStatement> buildBatch;
      private String spanName;
      private String oldSpanName;
      private String statement;
      private String oldStatement;
      private String summary;
      private Long batchSize;
      private String operation;
      private String oldOperation;
      private String collection;
      private String oldCollection;

      Builder(String name) {
        this.name = name;
      }

      Builder keyspace(String keyspace) {
        this.keyspace = keyspace;
        return this;
      }

      Builder buildBatch(Function<CqlSession, BatchStatement> buildBatch) {
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

      Builder statement(String statement) {
        this.statement = statement;
        return this;
      }

      Builder oldStatement(String oldStatement) {
        this.oldStatement = oldStatement;
        return this;
      }

      Builder summary(String summary) {
        this.summary = summary;
        return this;
      }

      Builder batchSize(long batchSize) {
        this.batchSize = batchSize;
        return this;
      }

      Builder operation(String operation) {
        this.operation = operation;
        return this;
      }

      Builder oldOperation(String oldOperation) {
        this.oldOperation = oldOperation;
        return this;
      }

      Builder collection(String collection) {
        this.collection = collection;
        return this;
      }

      Builder oldCollection(String oldCollection) {
        this.oldCollection = oldCollection;
        return this;
      }

      BatchScenario build() {
        return new BatchScenario(this);
      }
    }
  }
}
