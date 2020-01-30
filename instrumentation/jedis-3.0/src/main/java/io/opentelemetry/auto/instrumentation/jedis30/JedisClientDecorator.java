package io.opentelemetry.auto.instrumentation.jedis30;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.api.SpanTypes;
import io.opentelemetry.auto.decorator.DatabaseClientDecorator;
import io.opentelemetry.trace.Tracer;
import redis.clients.jedis.commands.ProtocolCommand;

public class JedisClientDecorator extends DatabaseClientDecorator<ProtocolCommand> {
  public static final JedisClientDecorator DECORATE = new JedisClientDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.jedis-1.4");

  @Override
  protected String service() {
    return "redis";
  }

  @Override
  protected String getComponentName() {
    return "redis-command";
  }

  @Override
  protected String getSpanType() {
    return SpanTypes.REDIS;
  }

  @Override
  protected String dbType() {
    return "redis";
  }

  @Override
  protected String dbUser(final ProtocolCommand session) {
    return null;
  }

  @Override
  protected String dbInstance(final ProtocolCommand session) {
    return null;
  }
}
