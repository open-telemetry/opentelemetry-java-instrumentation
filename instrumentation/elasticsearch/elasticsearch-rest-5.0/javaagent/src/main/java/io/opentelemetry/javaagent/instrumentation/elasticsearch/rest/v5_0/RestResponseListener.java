/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.v5_0;

import static io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.ElasticsearchRestClientTracer.tracer;

import io.opentelemetry.context.Context;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;

public class RestResponseListener implements ResponseListener {

  private final ResponseListener listener;
  private final Context context;

  public RestResponseListener(ResponseListener listener, Context context) {
    this.listener = listener;
    this.context = context;
  }

  @Override
  public void onSuccess(Response response) {
    if (response.getHost() != null) {
      tracer().onResponse(context, response);
    }
    tracer().end(context);

    listener.onSuccess(response);
  }

  @Override
  public void onFailure(Exception e) {
    tracer().endExceptionally(context, e);
    listener.onFailure(e);
  }
}
