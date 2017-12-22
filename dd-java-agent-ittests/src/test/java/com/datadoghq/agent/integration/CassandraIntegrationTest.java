package com.datadoghq.agent.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.datadoghq.trace.DDBaseSpan;
import com.datadoghq.trace.DDTracer;
import com.datadoghq.trace.writer.ListWriter;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import dd.test.TestUtils;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(ExpensiveTest.class)
public class CassandraIntegrationTest {
  private static final ListWriter writer = new ListWriter();
  private static final Tracer tracer = new DDTracer(writer);

  @BeforeClass
  public static void start() throws Exception {
    EmbeddedCassandraServerHelper.startEmbeddedCassandra(40000L);
    TestUtils.registerOrReplaceGlobalTracer(tracer);
  }

  @AfterClass
  public static void stop() {
    EmbeddedCassandraServerHelper.cleanEmbeddedCassandra();
  }

  @Test
  public void testSync() throws ClassNotFoundException {
    final Cluster cluster = EmbeddedCassandraServerHelper.getCluster();
    final Session session = cluster.newSession();
    assertThat(session.getClass().getName()).endsWith("contrib.cassandra.TracingSession");
    final int origSize = writer.size();

    session.execute("DROP KEYSPACE IF EXISTS sync_test");
    session.execute(
        "CREATE KEYSPACE sync_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':3}");
    session.execute("CREATE TABLE sync_test.users ( id UUID PRIMARY KEY, name text )");
    session.execute("INSERT INTO sync_test.users (id, name) values (uuid(), 'alice')");
    session.execute("SELECT * FROM sync_test.users where name = 'alice' ALLOW FILTERING");

    assertThat(writer.size()).isEqualTo(origSize + 5);
    final DDBaseSpan<?> selectTrace = writer.get(writer.size() - 1).get(0);

    assertThat(selectTrace.getServiceName()).isEqualTo(DDTracer.UNASSIGNED_DEFAULT_SERVICE_NAME);
    assertThat(selectTrace.getOperationName()).isEqualTo("execute");
    assertThat(selectTrace.getResourceName()).isEqualTo("execute");

    assertThat(selectTrace.getTags().get(Tags.COMPONENT.getKey())).isEqualTo("java-cassandra");
    assertThat(selectTrace.getTags().get(Tags.DB_STATEMENT.getKey()))
        .isEqualTo("SELECT * FROM sync_test.users where name = 'alice' ALLOW FILTERING");
    assertThat(selectTrace.getTags().get(Tags.DB_TYPE.getKey())).isEqualTo("cassandra");
    assertThat(selectTrace.getTags().get(Tags.PEER_HOSTNAME.getKey())).isEqualTo("localhost");
    // More info about IPv4 tag: https://trello.com/c/2el2IwkF/174-mongodb-ot-contrib-provides-a-wrong-peeripv4
    assertThat(selectTrace.getTags().get(Tags.PEER_HOST_IPV4.getKey())).isEqualTo(2130706433);
    assertThat(selectTrace.getTags().get(Tags.PEER_PORT.getKey())).isEqualTo(9142);
    assertThat(selectTrace.getTags().get(Tags.SPAN_KIND.getKey())).isEqualTo("client");
  }

  @Test
  public void testAsync() throws Exception {
    final Cluster cluster = EmbeddedCassandraServerHelper.getCluster();
    final Session session = cluster.connectAsync().get();
    assertThat(session.getClass().getName()).endsWith("contrib.cassandra.TracingSession");
    final int origSize = writer.size();

    session.executeAsync("DROP KEYSPACE IF EXISTS async_test").get();
    session
        .executeAsync(
            "CREATE KEYSPACE async_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':3}")
        .get();
    session.executeAsync("CREATE TABLE async_test.users ( id UUID PRIMARY KEY, name text )").get();
    session.executeAsync("INSERT INTO async_test.users (id, name) values (uuid(), 'alice')").get();
    session
        .executeAsync("SELECT * FROM async_test.users where name = 'alice' ALLOW FILTERING")
        .get();

    // traces are finished on another thread, so we have some waiting logic
    for (int timeout = 0; writer.size() < origSize + 5; timeout++) {
      if (timeout >= 10000) {
        Assert.fail("Cassandra async test timeout.");
      }
      Thread.sleep(1);
    }
    final DDBaseSpan<?> selectTrace = writer.get(writer.size() - 1).get(0);

    assertThat(selectTrace.getServiceName()).isEqualTo(DDTracer.UNASSIGNED_DEFAULT_SERVICE_NAME);
    assertThat(selectTrace.getOperationName()).isEqualTo("execute");
    assertThat(selectTrace.getResourceName()).isEqualTo("execute");

    assertThat(selectTrace.getTags().get(Tags.COMPONENT.getKey())).isEqualTo("java-cassandra");
    assertThat(selectTrace.getTags().get(Tags.DB_STATEMENT.getKey()))
        .isEqualTo("SELECT * FROM async_test.users where name = 'alice' ALLOW FILTERING");
    assertThat(selectTrace.getTags().get(Tags.DB_TYPE.getKey())).isEqualTo("cassandra");
    assertThat(selectTrace.getTags().get(Tags.PEER_HOSTNAME.getKey())).isEqualTo("localhost");
    // More info about IPv4 tag: https://trello.com/c/2el2IwkF/174-mongodb-ot-contrib-provides-a-wrong-peeripv4
    assertThat(selectTrace.getTags().get(Tags.PEER_HOST_IPV4.getKey())).isEqualTo(2130706433);
    assertThat(selectTrace.getTags().get(Tags.PEER_PORT.getKey())).isEqualTo(9142);
    assertThat(selectTrace.getTags().get(Tags.SPAN_KIND.getKey())).isEqualTo("client");
  }
}
