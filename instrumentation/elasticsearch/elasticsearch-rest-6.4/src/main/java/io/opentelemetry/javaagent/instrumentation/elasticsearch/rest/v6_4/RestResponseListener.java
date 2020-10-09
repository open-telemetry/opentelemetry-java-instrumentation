/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.v6_4;

import static io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.ElasticsearchRestClientTracer.TRACER;

import io.opentelemetry.trace.Span;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;

public class RestResponseListener implements ResponseListener {

  private final ResponseListener listener;
  private final Span span;

  public RestResponseListener(ResponseListener listener, Span span) {
    this.listener = listener;
    this.span = span;
  }

  @Override
  public void onSuccess(Response response) {
    if (response.getHost() != null) {
      TRACER.onResponse(span, response);
    }
    TRACER.end(span);

    listener.onSuccess(response);
  }

  @Override
  public void onFailure(Exception e) {
    TRACER.endExceptionally(span, e);
    listener.onFailure(e);
  }
}
