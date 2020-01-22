package io.opentelemetry.auto.instrumentation.spymemcached;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.api.SpanTypes;
import io.opentelemetry.auto.decorator.DatabaseClientDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import net.spy.memcached.MemcachedConnection;

public class MemcacheClientDecorator extends DatabaseClientDecorator<MemcachedConnection> {
  public static final MemcacheClientDecorator DECORATE = new MemcacheClientDecorator();

  public static final Tracer TRACER = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"spymemcached"};
  }

  @Override
  protected String service() {
    return "memcached";
  }

  @Override
  protected String component() {
    return "java-spymemcached";
  }

  @Override
  protected String spanType() {
    return SpanTypes.MEMCACHED;
  }

  @Override
  protected String dbType() {
    return "memcached";
  }

  @Override
  protected String dbUser(final MemcachedConnection session) {
    return null;
  }

  @Override
  protected String dbInstance(final MemcachedConnection connection) {
    return null;
  }

  public Span onOperation(final Span span, final String methodName) {

    final char[] chars =
        methodName
            .replaceFirst("^async", "")
            // 'CAS' name is special, we have to lowercase whole name
            .replaceFirst("^CAS", "cas")
            .toCharArray();

    // Lowercase first letter
    chars[0] = Character.toLowerCase(chars[0]);

    span.setAttribute(MoreTags.RESOURCE_NAME, new String(chars));
    return span;
  }
}
