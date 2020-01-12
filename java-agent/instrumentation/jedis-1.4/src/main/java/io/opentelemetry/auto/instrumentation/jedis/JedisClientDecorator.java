package io.opentelemetry.auto.instrumentation.jedis;

import io.opentelemetry.auto.api.SpanTypes;
import io.opentelemetry.auto.decorator.DatabaseClientDecorator;
import redis.clients.jedis.Protocol;

public class JedisClientDecorator extends DatabaseClientDecorator<Protocol.Command> {
  public static final JedisClientDecorator DECORATE = new JedisClientDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"jedis", "redis"};
  }

  @Override
  protected String service() {
    return "redis";
  }

  @Override
  protected String component() {
    return "redis-command";
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
  protected String dbUser(final Protocol.Command session) {
    return null;
  }

  @Override
  protected String dbInstance(final Protocol.Command session) {
    return null;
  }
}
