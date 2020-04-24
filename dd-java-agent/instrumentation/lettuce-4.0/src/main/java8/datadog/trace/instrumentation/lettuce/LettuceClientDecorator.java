package datadog.trace.instrumentation.lettuce;

import static datadog.trace.instrumentation.lettuce.InstrumentationPoints.getCommandResourceName;

import com.lambdaworks.redis.RedisURI;
import com.lambdaworks.redis.protocol.RedisCommand;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import datadog.trace.bootstrap.instrumentation.decorator.DatabaseClientDecorator;

public class LettuceClientDecorator extends DatabaseClientDecorator<RedisURI> {

  public static final LettuceClientDecorator DECORATE = new LettuceClientDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"lettuce"};
  }

  @Override
  protected String service() {
    return "redis";
  }

  @Override
  protected String component() {
    return "redis-client";
  }

  @Override
  protected String spanType() {
    return DDSpanTypes.REDIS;
  }

  @Override
  protected String dbType() {
    return "redis";
  }

  @Override
  protected String dbUser(RedisURI connection) {
    return null;
  }

  @Override
  protected String dbInstance(RedisURI connection) {
    return null;
  }

  @Override
  public AgentSpan onConnection(AgentSpan span, RedisURI connection) {
    if (connection != null) {
      span.setTag(Tags.PEER_HOSTNAME, connection.getHost());
      span.setTag(Tags.PEER_PORT, connection.getPort());
      span.setTag("db.redis.dbIndex", connection.getDatabase());
      span.setTag(DDTags.RESOURCE_NAME, resourceName(connection));
    }
    return super.onConnection(span, connection);
  }

  public AgentSpan onCommand(AgentSpan span, RedisCommand<?, ?, ?> command) {
    span.setTag(DDTags.RESOURCE_NAME,
        null == command ? "Redis Command" : getCommandResourceName(command.getType()));
    return span;
  }

  private static String resourceName(RedisURI connection) {
    return "CONNECT:"
      + connection.getHost()
      + ":"
      + connection.getPort()
      + "/"
      + connection.getDatabase();
  }
}
