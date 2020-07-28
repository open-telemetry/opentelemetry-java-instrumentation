/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.config.DefaultDriverOption
import com.datastax.oss.driver.api.core.config.DriverConfigLoader
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultDriverConfigLoader
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.trace.attributes.SemanticAttributes
import org.cassandraunit.utils.EmbeddedCassandraServerHelper

import java.time.Duration

import static io.opentelemetry.auto.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace
import static io.opentelemetry.trace.Span.Kind.CLIENT

class CassandraClientTest extends AgentTestRunner {

  def setupSpec() {
    /*
     This timeout seems excessive but we've seen tests fail with timeout of 40s.
     TODO: if we continue to see failures we may want to consider using 'real' Cassandra
     started in container like we do for memcached. Note: this will complicate things because
     tests would have to assume they run under shared Cassandra and act accordingly.
      */
    EmbeddedCassandraServerHelper.startEmbeddedCassandra(EmbeddedCassandraServerHelper.CASSANDRA_RNDPORT_YML_FILE, 120000L)
  }

  def cleanupSpec() {
    EmbeddedCassandraServerHelper.cleanEmbeddedCassandra()
  }

  def "test sync"() {
    setup:
    CqlSession session = getSession(keyspace)

    session.execute(statement)

    expect:
    assertTraces(1) {
      trace(0, 1) {
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
    CqlSession session = getSession(keyspace)

    runUnderTrace("parent") {
      session.executeAsync(statement).toCompletableFuture().get()
    }

    expect:
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        cassandraSpan(it, 1, statement, keyspace, span(0))
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
      operationName statement
      spanKind CLIENT
      if (parentSpan == null) {
        parent()
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
      }
    }
  }

  def getSession(String keyspace) {
    DriverConfigLoader configLoader = DefaultDriverConfigLoader.builder()
      .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(0))
      .build()
    return CqlSession.builder()
      .addContactPoint(new InetSocketAddress(EmbeddedCassandraServerHelper.getHost(), EmbeddedCassandraServerHelper.getNativeTransportPort()))
      .withConfigLoader(configLoader)
      .withLocalDatacenter("datacenter1")
      .withKeyspace((String) keyspace)
      .build()
  }

}
