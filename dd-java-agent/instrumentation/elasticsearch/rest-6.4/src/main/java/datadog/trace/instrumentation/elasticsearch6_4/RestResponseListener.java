package datadog.trace.instrumentation.elasticsearch6_4;

import datadog.trace.instrumentation.elasticsearch.ElasticsearchRestClientDecorator;
import io.opentracing.Span;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;

public class RestResponseListener implements ResponseListener {

  private final ResponseListener listener;
  private final Span span;

  public RestResponseListener(final ResponseListener listener, final Span span) {
    this.listener = listener;
    this.span = span;
  }

  @Override
  public void onSuccess(final Response response) {
    if (response.getHost() != null) {
      ElasticsearchRestClientDecorator.DECORATE.onResponse(span, response);
    }

    try {
      listener.onSuccess(response);
    } finally {
      ElasticsearchRestClientDecorator.DECORATE.beforeFinish(span);
      span.finish();
    }
  }

  @Override
  public void onFailure(final Exception e) {
    ElasticsearchRestClientDecorator.DECORATE.onError(span, e);

    try {
      listener.onFailure(e);
    } finally {
      ElasticsearchRestClientDecorator.DECORATE.beforeFinish(span);
      span.finish();
    }
  }
}
