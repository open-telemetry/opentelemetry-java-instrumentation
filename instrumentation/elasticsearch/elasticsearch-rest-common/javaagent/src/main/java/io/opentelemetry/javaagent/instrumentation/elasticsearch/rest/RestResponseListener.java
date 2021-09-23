/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;

public final class RestResponseListener implements ResponseListener {

  private final ResponseListener listener;
  private final Context parentContext;
  private final Instrumenter<String, Response> instrumenter;
  private final Context context;
  private final String request;

  public RestResponseListener(
      ResponseListener listener,
      Context parentContext,
      Instrumenter<String, Response> instrumenter,
      Context context,
      String request) {
    this.listener = listener;
    this.parentContext = parentContext;
    this.instrumenter = instrumenter;
    this.context = context;
    this.request = request;
  }

  @Override
  public void onSuccess(Response response) {
    instrumenter.end(context, request, response, null);
    try (Scope ignored = parentContext.makeCurrent()) {
      listener.onSuccess(response);
    }
  }

  @Override
  public void onFailure(Exception e) {
    instrumenter.end(context, request, null, e);
    try (Scope ignored = parentContext.makeCurrent()) {
      listener.onFailure(e);
    }
  }
}
