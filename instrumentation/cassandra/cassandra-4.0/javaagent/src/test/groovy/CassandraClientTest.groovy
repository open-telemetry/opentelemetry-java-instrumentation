/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.config.DefaultDriverOption
import com.datastax.oss.driver.api.core.config.DriverConfigLoader
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultDriverConfigLoader
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import spock.lang.Shared

import java.time.Duration

import static io.opentelemetry.api.trace.SpanKind.CLIENT

class CassandraClientTest extends AgentInstrumentationSpecification {
  private static final Logger logger = LoggerFactory.getLogger(CassandraClientTest)

  @Shared
  GenericContainer cassandra
  @Shared
  int cassandraPort

  def setupSpec() {
    cassandra = new GenericContainer("cassandra:4.0")
      // limit memory usage
      .withEnv("JVM_OPTS", "-Xmx128m -Xms128m")
      .withExposedPorts(9042)
      .withLogConsumer(new Slf4jLogConsumer(logger))
      .withStartupTimeout(Duration.ofSeconds(120))
    cassandra.start()

    cassandraPort = cassandra.getMappedPort(9042)
  }

  def cleanupSpec() {
    cassandra.stop()
  }

  def "test sync"() {
    setup:
    CqlSession session = getSession(keyspace)

    session.execute(statement)

    expect:
    assertTraces(1) {
      trace(0, 1) {
        cassandraSpan(it, 0, spanName, expectedStatement, operation, keyspace, table)
      }
    }

    cleanup:
    session.close()

    where:
    keyspace    | statement                                                                                         | expectedStatement                                                 | spanName                 | operation | table
    null        | "DROP KEYSPACE IF EXISTS sync_test"                                                               | "DROP KEYSPACE IF EXISTS sync_test"                               | "DB Query"               | null      | null
    null        | "CREATE KEYSPACE sync_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':3}" | "CREATE KEYSPACE sync_test WITH REPLICATION = {?:?, ?:?}"         | "DB Query"               | null      | null
    "sync_test" | "CREATE TABLE sync_test.users ( id UUID PRIMARY KEY, name text )"                                 | "CREATE TABLE sync_test.users ( id UUID PRIMARY KEY, name text )" | "sync_test"              | null      | null
    "sync_test" | "INSERT INTO sync_test.users (id, name) values (uuid(), 'alice')"                                 | "INSERT INTO sync_test.users (id, name) values (uuid(), ?)"       | "INSERT sync_test.users" | "INSERT"  | "sync_test.users"
    "sync_test" | "SELECT * FROM users where name = 'alice' ALLOW FILTERING"                                        | "SELECT * FROM users where name = ? ALLOW FILTERING"              | "SELECT sync_test.users" | "SELECT"  | "users"
  }

  def "test async"() {
    setup:
    CqlSession session = getSession(keyspace)

    runWithSpan("parent") {
      session.executeAsync(statement).toCompletableFuture().whenComplete({ result, throwable ->
        runWithSpan("child") {}
      }).get()
    }

    expect:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        cassandraSpan(it, 1, spanName, expectedStatement, operation, keyspace, table, span(0))
        span(2) {
          name "child"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
      }
    }

    cleanup:
    session.close()

    where:
    keyspace     | statement                                                                                          | expectedStatement                                                  | spanName                  | operation | table
    null         | "DROP KEYSPACE IF EXISTS async_test"                                                               | "DROP KEYSPACE IF EXISTS async_test"                               | "DB Query"                | null      | null
    null         | "CREATE KEYSPACE async_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':3}" | "CREATE KEYSPACE async_test WITH REPLICATION = {?:?, ?:?}"         | "DB Query"                | null      | null
    "async_test" | "CREATE TABLE async_test.users ( id UUID PRIMARY KEY, name text )"                                 | "CREATE TABLE async_test.users ( id UUID PRIMARY KEY, name text )" | "async_test"              | null      | null
    "async_test" | "INSERT INTO async_test.users (id, name) values (uuid(), 'alice')"                                 | "INSERT INTO async_test.users (id, name) values (uuid(), ?)"       | "INSERT async_test.users" | "INSERT"  | "async_test.users"
    "async_test" | "SELECT * FROM users where name = 'alice' ALLOW FILTERING"                                         | "SELECT * FROM users where name = ? ALLOW FILTERING"               | "SELECT async_test.users" | "SELECT"  | "users"
  }

  def cassandraSpan(TraceAssert trace, int index, String spanName, String statement, String operation, String keyspace, String table, Object parentSpan = null) {
    trace.span(index) {
      name spanName
      kind CLIENT
      if (parentSpan == null) {
        hasNoParent()
      } else {
        childOf((SpanData) parentSpan)
      }
      attributes {
        "$SemanticAttributes.NET_PEER_NAME" "localhost"
        "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
        "$SemanticAttributes.NET_PEER_PORT" cassandraPort
        "$SemanticAttributes.DB_SYSTEM" "cassandra"
        "$SemanticAttributes.DB_NAME" keyspace
        "$SemanticAttributes.DB_STATEMENT" statement
        "$SemanticAttributes.DB_OPERATION" operation
        "$SemanticAttributes.DB_CASSANDRA_CONSISTENCY_LEVEL" "LOCAL_ONE"
        "$SemanticAttributes.DB_CASSANDRA_COORDINATOR_DC" "datacenter1"
        "$SemanticAttributes.DB_CASSANDRA_COORDINATOR_ID" String
        "$SemanticAttributes.DB_CASSANDRA_IDEMPOTENCE" Boolean
        "$SemanticAttributes.DB_CASSANDRA_PAGE_SIZE" 5000
        "$SemanticAttributes.DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT" 0
        // the SqlStatementSanitizer can't handle CREATE statements yet
        "$SemanticAttributes.DB_CASSANDRA_TABLE" table
      }
    }
  }

  def getSession(String keyspace) {
    DriverConfigLoader configLoader = DefaultDriverConfigLoader.builder()
      .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(0))
      .build()
    return CqlSession.builder()
      .addContactPoint(new InetSocketAddress("localhost", cassandraPort))
      .withConfigLoader(configLoader)
      .withLocalDatacenter("datacenter1")
      .withKeyspace((String) keyspace)
      .build()
  }
}
