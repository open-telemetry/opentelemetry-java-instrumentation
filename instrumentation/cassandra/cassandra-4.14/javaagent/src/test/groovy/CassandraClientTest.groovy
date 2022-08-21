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
import reactor.core.publisher.Flux
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

  def "test reactive"() {
    setup:
    CqlSession session = getSession(keyspace)

    runWithSpan("parent") {
      Flux.from(session.executeReactive(statement)).doOnComplete({ result ->
        runWithSpan("child") {}
      }).blockLast()
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
    keyspace        | statement                                                                                              | expectedStatement                                                     | spanName                      | operation | table
    null            | "DROP KEYSPACE IF EXISTS reactive_test"                                                                | "DROP KEYSPACE IF EXISTS reactive_test"                               | "DB Query"                    | null      | null
    null            | "CREATE KEYSPACE reactive_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':3}"  | "CREATE KEYSPACE reactive_test WITH REPLICATION = {?:?, ?:?}"         | "DB Query"                    | null      | null
    "reactive_test" | "CREATE TABLE reactive_test.users ( id UUID PRIMARY KEY, name text )"                                  | "CREATE TABLE reactive_test.users ( id UUID PRIMARY KEY, name text )" | "reactive_test"               | null      | null
    "reactive_test" | "INSERT INTO reactive_test.users (id, name) values (uuid(), 'alice')"                                  | "INSERT INTO reactive_test.users (id, name) values (uuid(), ?)"       | "INSERT reactive_test.users"  | "INSERT"  | "reactive_test.users"
    "reactive_test" | "SELECT * FROM users where name = 'alice' ALLOW FILTERING"                                             | "SELECT * FROM users where name = ? ALLOW FILTERING"                  | "SELECT reactive_test.users"  | "SELECT"  | "users"
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
