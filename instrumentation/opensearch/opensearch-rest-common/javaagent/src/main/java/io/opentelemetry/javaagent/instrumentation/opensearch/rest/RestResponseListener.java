/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.function.Function;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseListener;

public final class RestResponseListener implements ResponseListener {

  private final ResponseListener listener;
  private final Context parentContext;
  private final Instrumenter<OpenSearchRestRequest, OpenSearchRestResponse> instrumenter;
  private final Context context;
  private final OpenSearchRestRequest request;
  private final Function<Response, OpenSearchRestResponse> responseConverter;

  public RestResponseListener(
      ResponseListener listener,
      Context parentContext,
      Instrumenter<OpenSearchRestRequest, OpenSearchRestResponse> instrumenter,
      Context context,
      OpenSearchRestRequest request,
      Function<Response, OpenSearchRestResponse> responseConverter) {
    this.listener = listener;
    this.parentContext = parentContext;
    this.instrumenter = instrumenter;
    this.context = context;
    this.request = request;
    this.responseConverter = responseConverter;
  }

  @Override
  public void onSuccess(Response response) {
    instrumenter.end(context, request, responseConverter.apply(response), null);
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
