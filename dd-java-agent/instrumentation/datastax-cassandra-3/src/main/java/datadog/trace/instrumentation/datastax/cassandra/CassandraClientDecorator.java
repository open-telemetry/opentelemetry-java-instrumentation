package datadog.trace.instrumentation.datastax.cassandra;

import com.datastax.driver.core.Host;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import datadog.trace.agent.decorator.DatabaseClientDecorator;
import datadog.trace.api.DDSpanTypes;
import io.opentracing.Span;
import io.opentracing.tag.Tags;

public class CassandraClientDecorator extends DatabaseClientDecorator<Session> {
  public static final CassandraClientDecorator DECORATE = new CassandraClientDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"cassandra"};
  }

  @Override
  protected String service() {
    return "cassandra";
  }

  @Override
  protected String component() {
    return "java-cassandra";
  }

  @Override
  protected String spanType() {
    return DDSpanTypes.CASSANDRA;
  }

  @Override
  protected String dbType() {
    return "cassandra";
  }

  @Override
  protected String dbUser(final Session session) {
    return null;
  }

  @Override
  protected String dbInstance(final Session session) {
    return session.getLoggedKeyspace();
  }

  public Span onResponse(final Span span, final ResultSet result) {
    if (result != null) {
      final Host host = result.getExecutionInfo().getQueriedHost();
      Tags.PEER_PORT.set(span, host.getSocketAddress().getPort());
      onPeerConnection(span, host.getSocketAddress().getAddress());
    }
    return span;
  }
}
