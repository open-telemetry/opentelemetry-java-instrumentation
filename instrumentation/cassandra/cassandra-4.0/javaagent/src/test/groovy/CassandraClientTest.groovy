/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.Span.Kind.CLIENT
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.config.DefaultDriverOption
import com.datastax.oss.driver.api.core.config.DriverConfigLoader
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultDriverConfigLoader
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData
import java.time.Duration
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import spock.lang.Shared

class CassandraClientTest extends AgentTestRunner {
  private static final Logger log = LoggerFactory.getLogger(CassandraClientTest)

  @Shared
  GenericContainer cassandra
  @Shared
  int cassandraPort

  def setupSpec() {
    cassandra = new GenericContainer("cassandra:4.0")
      .withExposedPorts(9042)
      .withLogConsumer(new Slf4jLogConsumer(log))
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
        cassandraSpan(it, 0, expectedStatement, keyspace)
      }
    }

    cleanup:
    session.close()

    where:
    keyspace    | statement                                                                                         | expectedStatement
    null        | "DROP KEYSPACE IF EXISTS sync_test"                                                               | "DROP KEYSPACE IF EXISTS sync_test"
    null        | "CREATE KEYSPACE sync_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':3}" | "CREATE KEYSPACE sync_test WITH REPLICATION = {?:?, ?:?}"
    "sync_test" | "CREATE TABLE sync_test.users ( id UUID PRIMARY KEY, name text )"                                 | "CREATE TABLE sync_test.users ( id UUID PRIMARY KEY, name text )"
    "sync_test" | "INSERT INTO sync_test.users (id, name) values (uuid(), 'alice')"                                 | "INSERT INTO sync_test.users (id, name) values (uuid(), ?)"
    "sync_test" | "SELECT * FROM users where name = 'alice' ALLOW FILTERING"                                        | "SELECT * FROM users where name = ? ALLOW FILTERING"
  }

  def "test async"() {
    setup:
    CqlSession session = getSession(keyspace)

    runUnderTrace("parent") {
      session.executeAsync(statement).toCompletableFuture().get()
    }

    expect:
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        cassandraSpan(it, 1, expectedStatement, keyspace, span(0))
      }
    }

    cleanup:
    session.close()

    where:
    keyspace     | statement                                                                                          | expectedStatement
    null         | "DROP KEYSPACE IF EXISTS async_test"                                                               | "DROP KEYSPACE IF EXISTS async_test"
    null         | "CREATE KEYSPACE async_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':3}" | "CREATE KEYSPACE async_test WITH REPLICATION = {?:?, ?:?}"
    "async_test" | "CREATE TABLE async_test.users ( id UUID PRIMARY KEY, name text )"                                 | "CREATE TABLE async_test.users ( id UUID PRIMARY KEY, name text )"
    "async_test" | "INSERT INTO async_test.users (id, name) values (uuid(), 'alice')"                                 | "INSERT INTO async_test.users (id, name) values (uuid(), ?)"
    "async_test" | "SELECT * FROM users where name = 'alice' ALLOW FILTERING"                                         | "SELECT * FROM users where name = ? ALLOW FILTERING"
  }

  def cassandraSpan(TraceAssert trace, int index, String statement, String keyspace, Object parentSpan = null, Throwable exception = null) {
    trace.span(index) {
      name statement
      kind CLIENT
      if (parentSpan == null) {
        hasNoParent()
      } else {
        childOf((SpanData) parentSpan)
      }
      attributes {
        "$SemanticAttributes.NET_PEER_NAME.key" "localhost"
        "$SemanticAttributes.NET_PEER_IP.key" "127.0.0.1"
        "$SemanticAttributes.NET_PEER_PORT.key" cassandraPort
        "$SemanticAttributes.DB_SYSTEM.key" "cassandra"
        "$SemanticAttributes.DB_NAME.key" keyspace
        "$SemanticAttributes.DB_STATEMENT.key" statement
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
