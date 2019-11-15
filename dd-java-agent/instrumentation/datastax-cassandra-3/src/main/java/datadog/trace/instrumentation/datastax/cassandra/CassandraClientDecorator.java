package datadog.trace.instrumentation.datastax.cassandra;

import com.datastax.driver.core.Host;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import datadog.trace.agent.decorator.DatabaseClientDecorator;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.instrumentation.api.AgentSpan;
import datadog.trace.instrumentation.api.Tags;

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

  public AgentSpan onResponse(final AgentSpan span, final ResultSet result) {
    if (result != null) {
      final Host host = result.getExecutionInfo().getQueriedHost();
      span.setTag(Tags.PEER_PORT, host.getSocketAddress().getPort());
      onPeerConnection(span, host.getSocketAddress().getAddress());
    }
    return span;
  }
}
