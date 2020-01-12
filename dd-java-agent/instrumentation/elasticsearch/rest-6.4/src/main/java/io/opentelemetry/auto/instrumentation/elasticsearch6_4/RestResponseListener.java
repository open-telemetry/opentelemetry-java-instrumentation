package io.opentelemetry.auto.instrumentation.elasticsearch6_4;

import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import io.opentelemetry.auto.instrumentation.elasticsearch.ElasticsearchRestClientDecorator;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;

public class RestResponseListener implements ResponseListener {

  private final ResponseListener listener;
  private final AgentSpan span;

  public RestResponseListener(final ResponseListener listener, final AgentSpan span) {
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
