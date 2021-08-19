/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest;

import static io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.ElasticsearchRestClientTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;

public class RestResponseListener implements ResponseListener {

  private final ResponseListener listener;
  private final Context context;
  private final Context parentContext;

  public RestResponseListener(ResponseListener listener, Context context, Context parentContext) {
    this.listener = listener;
    this.context = context;
    this.parentContext = parentContext;
  }

  @Override
  public void onSuccess(Response response) {
    if (response.getHost() != null) {
      tracer().onResponse(context, response);
    }
    tracer().end(context);

    try (Scope ignored = parentContext.makeCurrent()) {
      listener.onSuccess(response);
    }
  }

  @Override
  public void onFailure(Exception e) {
    tracer().endExceptionally(context, e);
    try (Scope ignored = parentContext.makeCurrent()) {
      listener.onFailure(e);
    }
  }
}
