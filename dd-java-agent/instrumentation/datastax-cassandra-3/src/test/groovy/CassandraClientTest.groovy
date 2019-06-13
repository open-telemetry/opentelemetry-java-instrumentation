import com.datastax.driver.core.Cluster
import com.datastax.driver.core.Session
import datadog.opentracing.DDSpan
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.api.DDSpanTypes
import io.opentracing.tag.Tags
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import spock.lang.Shared

import static datadog.trace.agent.test.utils.TraceUtils.basicSpan
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

class CassandraClientTest extends AgentTestRunner {

  @Shared
  Cluster cluster
  @Shared
  int port = 9142

  def setupSpec() {
    /*
     This timeout seems excessive but we've seen tests fail with timeout of 40s.
     TODO: if we continue to see failures we may want to consider using 'real' Cassandra
     started in container like we do for memcached. Note: this will complicate things because
     tests would have to assume they run under shared Cassandra and act accordingly.
      */
    EmbeddedCassandraServerHelper.startEmbeddedCassandra(120000L)

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
    Session session = cluster.connect(keyspace)
    runUnderTrace("parent") {
      session.executeAsync(statement)
      blockUntilChildSpansFinished(1)
    }

    expect:
    assertTraces(keyspace ? 2 : 1) {
      if (keyspace) {
        trace(0, 1) {
          cassandraSpan(it, 0, "USE $keyspace", null)
        }
      }
      trace(keyspace ? 1 : 0, 2) {
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
      serviceName "cassandra"
      operationName "cassandra.query"
      resourceName statement
      spanType DDSpanTypes.CASSANDRA
      if (parentSpan == null) {
        parent()
      } else {
        childOf((DDSpan) parentSpan)
      }
      tags {
        "$Tags.COMPONENT.key" "java-cassandra"
        "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
        "$Tags.DB_INSTANCE.key" keyspace
        "$Tags.DB_TYPE.key" "cassandra"
        "$Tags.PEER_HOSTNAME.key" "localhost"
        "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
        "$Tags.PEER_PORT.key" port
        defaultTags()
      }
    }
  }

}
