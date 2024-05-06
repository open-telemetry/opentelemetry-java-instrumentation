/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.junit.jupiter.api.Named.named;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

public class CassandraClientTest {

  private static final Logger logger = LoggerFactory.getLogger(CassandraClientTest.class);

  private static final Executor executor = Executors.newCachedThreadPool();

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @SuppressWarnings("rawtypes")
  private static GenericContainer cassandra;

  protected static String cassandraHost;

  protected static String cassandraIp;
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
    cassandra.start();

    cassandraHost = cassandra.getHost();
    cassandraIp = InetAddress.getByName(cassandra.getHost()).getHostAddress();
    cassandraPort = cassandra.getMappedPort(9042);
    cluster =
        Cluster.builder()
            .addContactPointsWithPorts(new InetSocketAddress(cassandra.getHost(), cassandraPort))
            .build();
  }

  @AfterAll
  static void afterAll() {
    cluster.close();
    cassandra.stop();
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("provideSyncParameters")
  void syncTest(Parameter parameter) {
    Session session = cluster.connect(parameter.keyspace);

    session.execute(parameter.statement);

    if (parameter.keyspace != null) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("DB Query")
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(
                              equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                              equalTo(ServerAttributes.SERVER_ADDRESS, cassandraHost),
                              equalTo(ServerAttributes.SERVER_PORT, cassandraPort),
                              equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, cassandraIp),
                              equalTo(NetworkAttributes.NETWORK_PEER_PORT, cassandraPort),
                              equalTo(DbIncubatingAttributes.DB_SYSTEM, "cassandra"),
                              equalTo(
                                  DbIncubatingAttributes.DB_STATEMENT,
                                  "USE " + parameter.keyspace))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName(parameter.spanName)
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(
                              equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                              equalTo(ServerAttributes.SERVER_ADDRESS, cassandraHost),
                              equalTo(ServerAttributes.SERVER_PORT, cassandraPort),
                              equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, cassandraIp),
                              equalTo(NetworkAttributes.NETWORK_PEER_PORT, cassandraPort),
                              equalTo(DbIncubatingAttributes.DB_SYSTEM, "cassandra"),
                              equalTo(DbIncubatingAttributes.DB_NAME, parameter.keyspace),
                              equalTo(
                                  DbIncubatingAttributes.DB_STATEMENT, parameter.expectedStatement),
                              equalTo(DbIncubatingAttributes.DB_OPERATION, parameter.operation),
                              equalTo(
                                  DbIncubatingAttributes.DB_CASSANDRA_TABLE, parameter.table))));
    } else {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName(parameter.spanName)
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(
                              equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                              equalTo(ServerAttributes.SERVER_ADDRESS, cassandraHost),
                              equalTo(ServerAttributes.SERVER_PORT, cassandraPort),
                              equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, cassandraIp),
                              equalTo(NetworkAttributes.NETWORK_PEER_PORT, cassandraPort),
                              equalTo(DbIncubatingAttributes.DB_SYSTEM, "cassandra"),
                              equalTo(
                                  DbIncubatingAttributes.DB_STATEMENT, parameter.expectedStatement),
                              equalTo(DbIncubatingAttributes.DB_OPERATION, parameter.operation),
                              equalTo(
                                  DbIncubatingAttributes.DB_CASSANDRA_TABLE, parameter.table))));
    }

    session.close();
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("provideAsyncParameters")
  void asyncTest(Parameter parameter) {
    @SuppressWarnings("WriteOnlyObject")
    AtomicBoolean callbackExecuted = new AtomicBoolean();
    Session session = cluster.connect(parameter.keyspace);

    testing.runWithSpan(
        "parent",
        () -> {
          ResultSetFuture future = session.executeAsync(parameter.statement);
          future.addListener(
              () -> testing.runWithSpan("callbackListener", () -> callbackExecuted.set(true)),
              executor);
        });

    if (parameter.keyspace != null) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("DB Query")
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(
                              equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                              equalTo(ServerAttributes.SERVER_ADDRESS, cassandraHost),
                              equalTo(ServerAttributes.SERVER_PORT, cassandraPort),
                              equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, cassandraIp),
                              equalTo(NetworkAttributes.NETWORK_PEER_PORT, cassandraPort),
                              equalTo(DbIncubatingAttributes.DB_SYSTEM, "cassandra"),
                              equalTo(
                                  DbIncubatingAttributes.DB_STATEMENT,
                                  "USE " + parameter.keyspace))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                  span ->
                      span.hasName(parameter.spanName)
                          .hasKind(SpanKind.CLIENT)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(
                              equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                              equalTo(ServerAttributes.SERVER_ADDRESS, cassandraHost),
                              equalTo(ServerAttributes.SERVER_PORT, cassandraPort),
                              equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, cassandraIp),
                              equalTo(NetworkAttributes.NETWORK_PEER_PORT, cassandraPort),
                              equalTo(DbIncubatingAttributes.DB_SYSTEM, "cassandra"),
                              equalTo(DbIncubatingAttributes.DB_NAME, parameter.keyspace),
                              equalTo(
                                  DbIncubatingAttributes.DB_STATEMENT, parameter.expectedStatement),
                              equalTo(DbIncubatingAttributes.DB_OPERATION, parameter.operation),
                              equalTo(DbIncubatingAttributes.DB_CASSANDRA_TABLE, parameter.table)),
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
                              equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                              equalTo(ServerAttributes.SERVER_ADDRESS, cassandraHost),
                              equalTo(ServerAttributes.SERVER_PORT, cassandraPort),
                              equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, cassandraIp),
                              equalTo(NetworkAttributes.NETWORK_PEER_PORT, cassandraPort),
                              equalTo(DbIncubatingAttributes.DB_SYSTEM, "cassandra"),
                              equalTo(
                                  DbIncubatingAttributes.DB_STATEMENT, parameter.expectedStatement),
                              equalTo(DbIncubatingAttributes.DB_OPERATION, parameter.operation),
                              equalTo(DbIncubatingAttributes.DB_CASSANDRA_TABLE, parameter.table)),
                  span ->
                      span.hasName("callbackListener")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(0))));
    }

    session.close();
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
                    "DROP",
                    "DROP",
                    null))),
        Arguments.of(
            named(
                "Create keyspace with replication",
                new Parameter(
                    null,
                    "CREATE KEYSPACE sync_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':3}",
                    "CREATE KEYSPACE sync_test WITH REPLICATION = {?:?, ?:?}",
                    "CREATE",
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
                    "SELECT sync_test.users",
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
                    "DROP",
                    "DROP",
                    null))),
        Arguments.of(
            named(
                "Create keyspace with replication",
                new Parameter(
                    null,
                    "CREATE KEYSPACE async_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':3}",
                    "CREATE KEYSPACE async_test WITH REPLICATION = {?:?, ?:?}",
                    "CREATE",
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
                    "SELECT async_test.users",
                    "SELECT",
                    "users"))));
  }

  private static class Parameter {
    public final String keyspace;
    public final String statement;
    public final String expectedStatement;
    public final String spanName;
    public final String operation;
    public final String table;

    public Parameter(
        String keyspace,
        String statement,
        String expectedStatement,
        String spanName,
        String operation,
        String table) {
      this.keyspace = keyspace;
      this.statement = statement;
      this.expectedStatement = expectedStatement;
      this.spanName = spanName;
      this.operation = operation;
      this.table = table;
    }
  }
}
