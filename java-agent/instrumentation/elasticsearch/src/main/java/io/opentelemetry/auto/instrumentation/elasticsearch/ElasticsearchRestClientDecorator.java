package io.opentelemetry.auto.instrumentation.elasticsearch;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.api.SpanTypes;
import io.opentelemetry.auto.decorator.DatabaseClientDecorator;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import org.elasticsearch.client.Response;

public class ElasticsearchRestClientDecorator extends DatabaseClientDecorator {
  public static final ElasticsearchRestClientDecorator DECORATE =
      new ElasticsearchRestClientDecorator();

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

  public Span onRequest(final Span span, final String method, final String endpoint) {
    span.setAttribute(Tags.HTTP_METHOD, method);
    span.setAttribute(Tags.HTTP_URL, endpoint);
    return span;
  }

  public Span onResponse(final Span span, final Response response) {
    if (response != null && response.getHost() != null) {
      span.setAttribute(Tags.PEER_HOSTNAME, response.getHost().getHostName());
      span.setAttribute(Tags.PEER_PORT, response.getHost().getPort());
    }
    return span;
  }
}
