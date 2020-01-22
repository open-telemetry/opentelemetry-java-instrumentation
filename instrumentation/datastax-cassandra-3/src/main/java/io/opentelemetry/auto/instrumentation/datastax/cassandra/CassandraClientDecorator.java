package io.opentelemetry.auto.instrumentation.datastax.cassandra;

import com.datastax.driver.core.Host;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import io.opentelemetry.auto.api.SpanTypes;
import io.opentelemetry.auto.decorator.DatabaseClientDecorator;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.trace.Span;

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
    return SpanTypes.CASSANDRA;
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
      span.setAttribute(Tags.PEER_PORT, host.getSocketAddress().getPort());
      onPeerConnection(span, host.getSocketAddress().getAddress());
    }
    return span;
  }
}
