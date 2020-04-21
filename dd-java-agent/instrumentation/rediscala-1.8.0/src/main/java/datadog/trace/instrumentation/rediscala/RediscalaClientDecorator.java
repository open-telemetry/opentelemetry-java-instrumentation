package datadog.trace.instrumentation.rediscala;

import datadog.trace.api.DDSpanTypes;
import datadog.trace.bootstrap.instrumentation.decorator.DatabaseClientDecorator;
import redis.RedisCommand;
import redis.protocol.RedisReply;

public class RediscalaClientDecorator
    extends DatabaseClientDecorator<RedisCommand<? extends RedisReply, ?>> {

  private static final String SERVICE_NAME = "redis";
  private static final String COMPONENT_NAME = SERVICE_NAME + "-command";

  public static final RediscalaClientDecorator DECORATE = new RediscalaClientDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"rediscala", "redis"};
  }

  @Override
  protected String service() {
    return SERVICE_NAME;
  }

  @Override
  protected String component() {
    return COMPONENT_NAME;
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
  protected String dbUser(final RedisCommand<? extends RedisReply, ?> session) {
    return null;
  }

  @Override
  protected String dbInstance(final RedisCommand<? extends RedisReply, ?> session) {
    return null;
  }
}
