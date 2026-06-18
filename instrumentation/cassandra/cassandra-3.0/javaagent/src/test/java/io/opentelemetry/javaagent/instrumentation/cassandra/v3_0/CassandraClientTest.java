/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
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
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_TABLE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.CASSANDRA;
import static org.junit.jupiter.api.Named.named;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

  // all batch scenarios share a single keyspace + table (recreated per scenario), so batch row ids
  // can be reused without worrying about collisions from previous scenarios
  private static final String BATCH_KEYSPACE = "batch_test";

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
                                  emitStableDatabaseSemconv()
                                      ? "USE " + parameter.keyspace
                                      : null))),
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
                                  emitStableDatabaseSemconv()
                                      ? "USE " + parameter.keyspace
                                      : null))),
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
                              equalTo(maybeStable(DB_CASSANDRA_TABLE), parameter.table)),
                  span ->
                      span.hasName("callbackListener")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(0))));
    }
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

  // describes the batch cases: a single-statement batch (which is executed as a normal statement,
  // not a batch), two statements with the same query, and two statements with different queries.
  // (an
  // empty batch is invalid CQL.) batch telemetry (db.operation.batch.size, BATCH span names and
  // summaries) is only emitted under stable database semconv
  @ParameterizedTest
  @MethodSource("batchScenarios")
  void batchStatement(BatchScenario scenario) {
    Session session = cluster.connect();
    cleanup.deferCleanup(session);

    session.execute("DROP KEYSPACE IF EXISTS " + BATCH_KEYSPACE);
    session.execute(
        "CREATE KEYSPACE "
            + BATCH_KEYSPACE
            + " WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':1}");
    session.execute("CREATE TABLE " + BATCH_KEYSPACE + ".records ( id int PRIMARY KEY, num int )");
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
                                    ? scenario.statement
                                    : scenario.oldStatement),
                            equalTo(
                                DB_OPERATION_BATCH_SIZE,
                                emitStableDatabaseSemconv() ? scenario.batchSize : null),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? scenario.summary : null),
                            // under stable semconv a batch carries db.operation.name (BATCH, or
                            // BATCH <operation> when all statements share one operation) and
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
                                    : scenario.oldCollection))));
  }

  private static Stream<BatchScenario> batchScenarios() {
    return Stream.of(
        // an empty batch still produces a client span carrying db.operation.batch.size 0, but with
        // no query text, summary or operation; the stable span name is BATCH
        BatchScenario.builder("empty")
            .buildBatch(session -> new BatchStatement())
            .spanName("BATCH")
            .oldSpanName("DB Query")
            .batchSize(0)
            .build(),
        // a single-statement batch is executed as a normal statement (not a batch): it has the
        // normal INSERT span name in both modes, db.operation and db.cassandra.table, and no
        // db.operation.batch.size
        BatchScenario.builder("single")
            .buildBatch(
                session -> {
                  PreparedStatement insert =
                      session.prepare("INSERT INTO batch_test.records (id, num) values (?, ?)");
                  return new BatchStatement().add(insert.bind(1, 1));
                })
            .spanName("INSERT batch_test.records")
            .oldSpanName("INSERT batch_test.records")
            .statement("INSERT INTO batch_test.records (id, num) values (?, ?)")
            .oldStatement("INSERT INTO batch_test.records (id, num) values (?, ?)")
            .summary("INSERT batch_test.records")
            .operation("INSERT")
            .oldOperation("INSERT")
            .collection("batch_test.records")
            .oldCollection("batch_test.records")
            .build(),
        BatchScenario.builder("twoSameOperation")
            .buildBatch(
                session -> {
                  PreparedStatement insert =
                      session.prepare("INSERT INTO batch_test.records (id, num) values (?, ?)");
                  return new BatchStatement().add(insert.bind(1, 1)).add(insert.bind(2, 2));
                })
            .spanName("BATCH INSERT batch_test.records")
            .oldSpanName("DB Query")
            .statement("INSERT INTO batch_test.records (id, num) values (?, ?)")
            .summary("BATCH INSERT batch_test.records")
            .operation("BATCH INSERT")
            .collection("batch_test.records")
            .batchSize(2)
            .build(),
        BatchScenario.builder("twoDifferentOperations")
            .buildBatch(
                session -> {
                  PreparedStatement insert =
                      session.prepare("INSERT INTO batch_test.records (id, num) values (4, ?)");
                  return new BatchStatement()
                      .add(insert.bind(4))
                      .add(
                          new SimpleStatement(
                              "UPDATE batch_test.records SET num = 5 WHERE id = 4"));
                })
            .spanName("BATCH")
            .oldSpanName("DB Query")
            .statement(
                "INSERT INTO batch_test.records (id, num) values (4, ?); UPDATE batch_test.records SET num = ? WHERE id = ?")
            .summary("BATCH")
            .operation("BATCH")
            .collection("batch_test.records")
            .batchSize(2)
            .build());
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

  private static final class BatchScenario {
    final String name;
    final Function<Session, BatchStatement> buildBatch;
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
      private Function<Session, BatchStatement> buildBatch;
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
