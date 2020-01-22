package io.opentelemetry.auto.instrumentation.elasticsearch;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.api.SpanTypes;
import io.opentelemetry.auto.decorator.DatabaseClientDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

public class ElasticsearchTransportClientDecorator extends DatabaseClientDecorator {
  public static final ElasticsearchTransportClientDecorator DECORATE =
      new ElasticsearchTransportClientDecorator();

  public static final Tracer TRACER = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"elasticsearch"};
  }

  @Override
  protected String service() {
    return "elasticsearch";
  }

  @Override
  protected String component() {
    return "elasticsearch-java";
  }

  @Override
  protected String spanType() {
    return SpanTypes.ELASTICSEARCH;
  }

  @Override
  protected String dbType() {
    return "elasticsearch";
  }

  @Override
  protected String dbUser(final Object o) {
    return null;
  }

  @Override
  protected String dbInstance(final Object o) {
    return null;
  }

  public Span onRequest(final Span span, final Class action, final Class request) {
    if (action != null) {
      span.setAttribute(MoreTags.RESOURCE_NAME, action.getSimpleName());
      span.setAttribute("elasticsearch.action", action.getSimpleName());
    }
    if (request != null) {
      span.setAttribute("elasticsearch.request", request.getSimpleName());
    }
    return span;
  }
}
