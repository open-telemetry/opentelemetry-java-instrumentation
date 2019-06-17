package datadog.trace.instrumentation.lettuce;

import datadog.trace.agent.decorator.DatabaseClientDecorator;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.lettuce.core.RedisURI;
import io.lettuce.core.protocol.RedisCommand;
import io.opentracing.Span;
import io.opentracing.tag.Tags;

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
  public Span onConnection(final Span span, final RedisURI connection) {
    if (connection != null) {
      Tags.PEER_HOSTNAME.set(span, connection.getHost());
      Tags.PEER_PORT.set(span, connection.getPort());

      span.setTag("db.redis.dbIndex", connection.getDatabase());
      span.setTag(DDTags.RESOURCE_NAME, "CONNECT:" + connection.getHost()
        + ":" + connection.getPort() + "/" + connection.getDatabase());
    }
    return super.onConnection(span, connection);
  }

  public Span onCommand(final Span span, final RedisCommand command) {
    final String commandName = LettuceInstrumentationUtil.getCommandName(command);
    span.setTag(
        DDTags.RESOURCE_NAME, LettuceInstrumentationUtil.getCommandResourceName(commandName));
    return span;
  }
}
