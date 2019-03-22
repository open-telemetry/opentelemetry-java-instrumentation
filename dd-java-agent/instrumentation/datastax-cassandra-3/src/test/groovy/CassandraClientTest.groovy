import com.datastax.driver.core.Cluster
import com.datastax.driver.core.Session
import datadog.opentracing.DDSpan
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import io.opentracing.tag.Tags
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import spock.lang.Shared

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

  def "sync traces"() {
    setup:
    final Session session = cluster.newSession()

    session.execute("DROP KEYSPACE IF EXISTS sync_test")
    session.execute(
      "CREATE KEYSPACE sync_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':3}")
    session.execute("CREATE TABLE sync_test.users ( id UUID PRIMARY KEY, name text )")
    session.execute("INSERT INTO sync_test.users (id, name) values (uuid(), 'alice')")
    session.execute("SELECT * FROM sync_test.users where name = 'alice' ALLOW FILTERING")

    def query = "SELECT * FROM sync_test.users where name = 'alice' ALLOW FILTERING"

    expect:
    session.getClass().getName().endsWith("cassandra.TracingSession")
    TEST_WRITER.size() == 5
    final DDSpan selectTrace = TEST_WRITER.get(TEST_WRITER.size() - 1).get(0)

    selectTrace.getServiceName() == "cassandra"
    selectTrace.getOperationName() == "cassandra.query"
    selectTrace.getResourceName() == query
    selectTrace.getSpanType() == DDSpanTypes.CASSANDRA

    selectTrace.getTags().get(Tags.COMPONENT.getKey()) == "java-cassandra"
    selectTrace.getTags().get(Tags.DB_TYPE.getKey()) == "cassandra"
    selectTrace.getTags().get(Tags.PEER_HOSTNAME.getKey()) == "localhost"
    // More info about IPv4 tag: https://trello.com/c/2el2IwkF/174-mongodb-ot-contrib-provides-a-wrong-peeripv4
    selectTrace.getTags().get(Tags.PEER_HOST_IPV4.getKey()) == 2130706433
    selectTrace.getTags().get(Tags.PEER_PORT.getKey()) == 9142
    selectTrace.getTags().get(Tags.SPAN_KIND.getKey()) == "client"
  }

  def "async traces"() {
    setup:
    final Session session = cluster.connectAsync().get()

    session.executeAsync("DROP KEYSPACE IF EXISTS async_test").get()
    session
      .executeAsync(
      "CREATE KEYSPACE async_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':3}")
      .get()
    session.executeAsync("CREATE TABLE async_test.users ( id UUID PRIMARY KEY, name text )").get()
    session.executeAsync("INSERT INTO async_test.users (id, name) values (uuid(), 'alice')").get()
    TEST_WRITER.waitForTraces(4)
    session
      .executeAsync("SELECT * FROM async_test.users where name = 'alice' ALLOW FILTERING")
      .get()
    TEST_WRITER.waitForTraces(5)

    def query = "SELECT * FROM async_test.users where name = 'alice' ALLOW FILTERING"

    expect:
    session.getClass().getName().endsWith("cassandra.TracingSession")
    final DDSpan selectTrace = TEST_WRITER.get(TEST_WRITER.size() - 1).get(0)

    selectTrace.getServiceName() == "cassandra"
    selectTrace.getOperationName() == "cassandra.query"
    selectTrace.getResourceName() == query
    selectTrace.getSpanType() == DDSpanTypes.CASSANDRA

    selectTrace.getTags().get(Tags.COMPONENT.getKey()) == "java-cassandra"
    selectTrace.getTags().get(Tags.DB_TYPE.getKey()) == "cassandra"
    selectTrace.getTags().get(Tags.PEER_HOSTNAME.getKey()) == "localhost"
    // More info about IPv4 tag: https://trello.com/c/2el2IwkF/174-mongodb-ot-contrib-provides-a-wrong-peeripv4
    selectTrace.getTags().get(Tags.PEER_HOST_IPV4.getKey()) == 2130706433
    selectTrace.getTags().get(Tags.PEER_PORT.getKey()) == 9142
    selectTrace.getTags().get(Tags.SPAN_KIND.getKey()) == "client"
  }
}
