/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace
import static io.opentelemetry.trace.Span.Kind.CLIENT

import com.datastax.driver.core.Cluster
import com.datastax.driver.core.Session
import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.trace.attributes.SemanticAttributes
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import spock.lang.Shared

class CassandraClientTest extends AgentTestRunner {

  @Shared
  Cluster cluster

  @Shared
  def executor = Executors.newCachedThreadPool()

  def setupSpec() {
    /*
     This timeout seems excessive but we've seen tests fail with timeout of 40s.
     TODO: if we continue to see failures we may want to consider using 'real' Cassandra
     started in container like we do for memcached. Note: this will complicate things because
     tests would have to assume they run under shared Cassandra and act accordingly.
      */
    EmbeddedCassandraServerHelper.startEmbeddedCassandra(EmbeddedCassandraServerHelper.CASSANDRA_RNDPORT_YML_FILE, 120000L)

    cluster = EmbeddedCassandraServerHelper.getCluster()

    /*
    Looks like sometimes our requests fail because Cassandra takes to long to respond,
    Increase this timeout as well to try to cope with this.
     */
    cluster.getConfiguration().getSocketOptions().setReadTimeoutMillis(120000)
  }

  def cleanupSpec() {
    EmbeddedCassandraServerHelper.cleanEmbeddedCassandra()
  }

  def "test sync"() {
    setup:
    Session session = cluster.connect(keyspace)

    session.execute(statement)

    expect:
    assertTraces(keyspace ? 2 : 1) {
      if (keyspace) {
        trace(0, 1) {
          cassandraSpan(it, 0, "USE $keyspace", null)
        }
      }
      trace(keyspace ? 1 : 0, 1) {
        cassandraSpan(it, 0, statement, keyspace)
      }
    }

    cleanup:
    session.close()

    where:
    statement                                                                                         | keyspace
    "DROP KEYSPACE IF EXISTS sync_test"                                                               | null
    "CREATE KEYSPACE sync_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':3}" | null
    "CREATE TABLE sync_test.users ( id UUID PRIMARY KEY, name text )"                                 | "sync_test"
    "INSERT INTO sync_test.users (id, name) values (uuid(), 'alice')"                                 | "sync_test"
    "SELECT * FROM users where name = 'alice' ALLOW FILTERING"                                        | "sync_test"
  }

  def "test async"() {
    setup:
    def callbackExecuted = new AtomicBoolean()
    Session session = cluster.connect(keyspace)
    runUnderTrace("parent") {
      def future = session.executeAsync(statement)
      future.addListener({ ->
        runUnderTrace("callbackListener") {
          callbackExecuted.set(true)
        }
      }, executor)
    }

    expect:
    assertTraces(keyspace ? 2 : 1) {
      if (keyspace) {
        trace(0, 1) {
          cassandraSpan(it, 0, "USE $keyspace", null)
        }
      }
      trace(keyspace ? 1 : 0, 3) {
        basicSpan(it, 0, "parent")
        cassandraSpan(it, 1, statement, keyspace, span(0))
        basicSpan(it, 2, "callbackListener", span(0))
      }
    }

    cleanup:
    session.close()

    where:
    statement                                                                                          | keyspace
    "DROP KEYSPACE IF EXISTS async_test"                                                               | null
    "CREATE KEYSPACE async_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':3}" | null
    "CREATE TABLE async_test.users ( id UUID PRIMARY KEY, name text )"                                 | "async_test"
    "INSERT INTO async_test.users (id, name) values (uuid(), 'alice')"                                 | "async_test"
    "SELECT * FROM users where name = 'alice' ALLOW FILTERING"                                         | "async_test"
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
        "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
        "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
        "${SemanticAttributes.NET_PEER_PORT.key()}" EmbeddedCassandraServerHelper.getNativeTransportPort()
        "${SemanticAttributes.DB_SYSTEM.key()}" "cassandra"
        "${SemanticAttributes.DB_NAME.key()}" keyspace
        "${SemanticAttributes.DB_STATEMENT.key()}" statement
        "${SemanticAttributes.CASSANDRA_KEYSPACE.key()}" keyspace
      }
    }
  }

}
