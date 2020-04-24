package datadog.trace.instrumentation.lettuce;

import static datadog.trace.instrumentation.lettuce.LettuceInstrumentationUtil.getCommandResourceName;

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
  protected String dbUser(final RedisURI connection) {
    return null;
  }

  @Override
  protected String dbInstance(final RedisURI connection) {
    return null;
  }

  @Override
  public AgentSpan onConnection(final AgentSpan span, final RedisURI connection) {
    if (connection != null) {
      span.setTag(Tags.PEER_HOSTNAME, connection.getHost());
      span.setTag(Tags.PEER_PORT, connection.getPort());
      span.setTag("db.redis.dbIndex", connection.getDatabase());
      span.setTag(
          DDTags.RESOURCE_NAME,
          "CONNECT:"
              + connection.getHost()
              + ":"
              + connection.getPort()
              + "/"
              + connection.getDatabase());
    }
    return super.onConnection(span, connection);
  }

  @SuppressWarnings("rawtypes")
  public AgentSpan onCommand(final AgentSpan span, final RedisCommand command) {
      span.setTag(DDTags.RESOURCE_NAME,
        null == command ? "Redis Command" : getCommandResourceName(command.getType()));
    return span;
  }
}
