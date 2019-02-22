package datadog.trace.instrumentation.elasticsearch;

import datadog.trace.agent.decorator.DatabaseClientDecorator;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Span;

public class ElasticsearchTransportClientDecorator extends DatabaseClientDecorator {
  public static final ElasticsearchTransportClientDecorator DECORATE =
      new ElasticsearchTransportClientDecorator();

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
    return DDSpanTypes.ELASTICSEARCH;
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
      span.setTag(DDTags.RESOURCE_NAME, action.getSimpleName());
      span.setTag("elasticsearch.action", action.getSimpleName());
    }
    if (request != null) {
      span.setTag("elasticsearch.request", request.getSimpleName());
    }
    return span;
  }
}
