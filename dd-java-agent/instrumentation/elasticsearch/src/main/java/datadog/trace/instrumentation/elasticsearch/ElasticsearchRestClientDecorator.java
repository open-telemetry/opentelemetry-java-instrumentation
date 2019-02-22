package datadog.trace.instrumentation.elasticsearch;

import datadog.trace.agent.decorator.DatabaseClientDecorator;
import datadog.trace.api.DDSpanTypes;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import org.elasticsearch.client.Response;

public class ElasticsearchRestClientDecorator extends DatabaseClientDecorator {
  public static final ElasticsearchRestClientDecorator DECORATE =
      new ElasticsearchRestClientDecorator();

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

  public Span onRequest(final Span span, final String method, final String endpoint) {
    Tags.HTTP_METHOD.set(span, method);
    Tags.HTTP_URL.set(span, endpoint);
    return span;
  }

  public Span onResponse(final Span span, final Response response) {
    if (response != null && response.getHost() != null) {
      Tags.PEER_HOSTNAME.set(span, response.getHost().getHostName());
      Tags.PEER_PORT.set(span, response.getHost().getPort());
    }
    return span;
  }
}
