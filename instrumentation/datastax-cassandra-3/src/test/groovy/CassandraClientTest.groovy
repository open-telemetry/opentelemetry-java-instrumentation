import com.datastax.driver.core.Cluster
import com.datastax.driver.core.Session
import io.opentelemetry.auto.api.Config
import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.SpanData
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import spock.lang.Shared

import static io.opentelemetry.auto.test.utils.ConfigUtils.withConfigOverride
import static io.opentelemetry.auto.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace

class CassandraClientTest extends AgentTestRunner {

  @Shared
  Cluster cluster

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

    withConfigOverride(Config.DB_CLIENT_HOST_SPLIT_BY_INSTANCE, "$renameService") {
      session.execute(statement)
    }

    expect:
    assertTraces(keyspace ? 2 : 1) {
      if (keyspace) {
        trace(0, 1) {
          cassandraSpan(it, 0, "USE $keyspace", null, false)
        }
      }
      trace(keyspace ? 1 : 0, 1) {
        cassandraSpan(it, 0, statement, keyspace, renameService)
      }
    }

    cleanup:
    session.close()

    where:
    statement                                                                                         | keyspace    | renameService
    "DROP KEYSPACE IF EXISTS sync_test"                                                               | null        | false
    "CREATE KEYSPACE sync_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':3}" | null        | true
    "CREATE TABLE sync_test.users ( id UUID PRIMARY KEY, name text )"                                 | "sync_test" | false
    "INSERT INTO sync_test.users (id, name) values (uuid(), 'alice')"                                 | "sync_test" | false
    "SELECT * FROM users where name = 'alice' ALLOW FILTERING"                                        | "sync_test" | true
  }

  def "test async"() {
    setup:
    Session session = cluster.connect(keyspace)
    runUnderTrace("parent") {
      withConfigOverride(Config.DB_CLIENT_HOST_SPLIT_BY_INSTANCE, "$renameService") {
        session.executeAsync(statement)
      }
    }

    expect:
    assertTraces(keyspace ? 2 : 1) {
      if (keyspace) {
        trace(0, 1) {
          cassandraSpan(it, 0, "USE $keyspace", null, false)
        }
      }
      trace(keyspace ? 1 : 0, 2) {
        basicSpan(it, 0, "parent")
        cassandraSpan(it, 1, statement, keyspace, renameService, span(0))
      }
    }

    cleanup:
    session.close()

    where:
    statement                                                                                          | keyspace     | renameService
    "DROP KEYSPACE IF EXISTS async_test"                                                               | null         | false
    "CREATE KEYSPACE async_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':3}" | null         | true
    "CREATE TABLE async_test.users ( id UUID PRIMARY KEY, name text )"                                 | "async_test" | false
    "INSERT INTO async_test.users (id, name) values (uuid(), 'alice')"                                 | "async_test" | false
    "SELECT * FROM users where name = 'alice' ALLOW FILTERING"                                         | "async_test" | true
  }

  def cassandraSpan(TraceAssert trace, int index, String statement, String keyspace, boolean renameService, Object parentSpan = null, Throwable exception = null) {
    trace.span(index) {
      operationName "cassandra.query"
      if (parentSpan == null) {
        parent()
      } else {
        childOf((SpanData) parentSpan)
      }
      tags {
        "$MoreTags.SERVICE_NAME" renameService && keyspace ? keyspace : "cassandra"
        "$MoreTags.SPAN_TYPE" SpanTypes.CASSANDRA
        "$Tags.COMPONENT" "java-cassandra"
        "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
        "$Tags.PEER_HOSTNAME" "localhost"
        "$Tags.PEER_HOST_IPV4" "127.0.0.1"
        "$Tags.PEER_PORT" EmbeddedCassandraServerHelper.getNativeTransportPort()
        "$Tags.DB_TYPE" "cassandra"
        "$Tags.DB_INSTANCE" keyspace
        "$Tags.DB_STATEMENT" statement
      }
    }
  }

}
