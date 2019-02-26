package datadog.trace.instrumentation.datastax.cassandra;

import com.datastax.driver.core.Host;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import datadog.trace.agent.decorator.DatabaseClientDecorator;
import datadog.trace.api.DDSpanTypes;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;

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
      Tags.PEER_HOSTNAME.set(span, host.getAddress().getHostName());

      final InetAddress inetAddress = host.getSocketAddress().getAddress();
      if (inetAddress instanceof Inet4Address) {
        final byte[] address = inetAddress.getAddress();
        Tags.PEER_HOST_IPV4.set(span, ByteBuffer.wrap(address).getInt());
      } else {
        Tags.PEER_HOST_IPV6.set(span, inetAddress.getHostAddress());
      }
    }
    return span;
  }
}
