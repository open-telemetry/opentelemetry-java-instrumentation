package io.opentelemetry.auto.instrumentation.lettuce;

import io.lettuce.core.RedisURI;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.api.SpanTypes;
import io.opentelemetry.auto.decorator.DatabaseClientDecorator;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

public class LettuceClientDecorator extends DatabaseClientDecorator<RedisURI> {
  public static final LettuceClientDecorator DECORATE = new LettuceClientDecorator();

  public static final Tracer TRACER = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  @Override
  protected String service() {
    return "redis";
  }

  @Override
  protected String getComponentName() {
    return "redis-client";
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
      span.setAttribute(Tags.PEER_HOSTNAME, connection.getHost());
      span.setAttribute(Tags.PEER_PORT, connection.getPort());

      span.setAttribute("db.redis.dbIndex", connection.getDatabase());
      span.setAttribute(
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

  public Span onCommand(final Span span, final RedisCommand command) {
    final String commandName = LettuceInstrumentationUtil.getCommandName(command);
    span.setAttribute(
        MoreTags.RESOURCE_NAME, LettuceInstrumentationUtil.getCommandResourceName(commandName));
    return span;
  }
}
