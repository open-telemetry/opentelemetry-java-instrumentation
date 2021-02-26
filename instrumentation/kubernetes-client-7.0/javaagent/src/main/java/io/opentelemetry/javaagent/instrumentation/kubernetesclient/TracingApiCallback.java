/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import static io.opentelemetry.javaagent.instrumentation.kubernetesclient.KubernetesClientTracer.tracer;

import io.kubernetes.client.openapi.ApiCallback;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.ApiResponse;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.List;
import java.util.Map;

public class TracingApiCallback<T> implements ApiCallback<T> {
  private final ApiCallback<T> delegate;
  private final Context parentContext;
  private final Context context;

  public TracingApiCallback(ApiCallback<T> delegate, Context parentContext, Context context) {
    this.delegate = delegate;
    this.parentContext = parentContext;
    this.context = context;
  }

  @Override
  public void onFailure(ApiException e, int status, Map<String, List<String>> headers) {
    tracer().endExceptionally(context, new ApiResponse<>(status, headers), e);
    if (delegate != null) {
      try (Scope ignored = parentContext.makeCurrent()) {
        delegate.onFailure(e, status, headers);
      }
    }
  }

  @Override
  public void onSuccess(T t, int status, Map<String, List<String>> headers) {
    tracer().end(context, new ApiResponse<>(status, headers));
    if (delegate != null) {
      try (Scope ignored = parentContext.makeCurrent()) {
        delegate.onSuccess(t, status, headers);
      }
    }
  }

  @Override
  public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
    if (delegate != null) {
      try (Scope ignored = parentContext.makeCurrent()) {
        delegate.onUploadProgress(bytesWritten, contentLength, done);
      }
    }
  }

  @Override
  public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
    if (delegate != null) {
      try (Scope ignored = parentContext.makeCurrent()) {
        delegate.onDownloadProgress(bytesRead, contentLength, done);
      }
    }
  }
}
