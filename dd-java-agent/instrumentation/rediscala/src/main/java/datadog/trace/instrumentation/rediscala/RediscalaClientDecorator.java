package datadog.trace.instrumentation.rediscala;

import datadog.trace.agent.decorator.DatabaseClientDecorator;
import datadog.trace.api.DDSpanTypes;
import redis.RedisCommand;

public class RediscalaClientDecorator extends DatabaseClientDecorator<RedisCommand> {
  public static final RediscalaClientDecorator DECORATE = new RediscalaClientDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"rediscala", "redis"};
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
    return DDSpanTypes.REDIS;
  }

  @Override
  protected String dbType() {
    return "redis";
  }

  @Override
  protected String dbUser(final RedisCommand session) {
    return null;
  }

  @Override
  protected String dbInstance(final RedisCommand session) {
    return null;
  }
}
