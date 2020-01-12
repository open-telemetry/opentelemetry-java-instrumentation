package io.opentelemetry.auto.instrumentation.lettuce;

import io.opentelemetry.auto.agent.decorator.DatabaseClientDecorator;
import io.opentelemetry.auto.api.SpanTypes;
import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.lettuce.core.RedisURI;
import io.lettuce.core.protocol.RedisCommand;

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
    return SpanTypes.REDIS;
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
          MoreTags.RESOURCE_NAME,
          "CONNECT:"
              + connection.getHost()
              + ":"
              + connection.getPort()
              + "/"
              + connection.getDatabase());
    }
    return super.onConnection(span, connection);
  }

  public AgentSpan onCommand(final AgentSpan span, final RedisCommand command) {
    final String commandName = LettuceInstrumentationUtil.getCommandName(command);
    span.setTag(
        MoreTags.RESOURCE_NAME, LettuceInstrumentationUtil.getCommandResourceName(commandName));
    return span;
  }
}
