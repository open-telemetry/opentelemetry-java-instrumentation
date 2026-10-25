/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseListener;

public class RestResponseListener implements ResponseListener {

  @FunctionalInterface
  public interface ExceptionConverter {
    @Nullable
    OpenSearchRestResponse convert(Throwable error);
  }

  private final ResponseListener listener;
  private final Context parentContext;
  private final Instrumenter<OpenSearchRestRequest, OpenSearchRestResponse> instrumenter;
  private final Context context;
  private final OpenSearchRestRequest request;
  private final Function<Response, OpenSearchRestResponse> responseConverter;
  private final ExceptionConverter exceptionConverter;

  public RestResponseListener(
      ResponseListener listener,
      Context parentContext,
      Instrumenter<OpenSearchRestRequest, OpenSearchRestResponse> instrumenter,
      Context context,
      OpenSearchRestRequest request,
      Function<Response, OpenSearchRestResponse> responseConverter,
      ExceptionConverter exceptionConverter) {
    this.listener = listener;
    this.parentContext = parentContext;
    this.instrumenter = instrumenter;
    this.context = context;
    this.request = request;
    this.responseConverter = responseConverter;
    this.exceptionConverter = exceptionConverter;
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
    instrumenter.end(context, request, exceptionConverter.convert(e), e);
    try (Scope ignored = parentContext.makeCurrent()) {
      listener.onFailure(e);
    }
  }
}
