/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import static io.opentelemetry.javaagent.instrumentation.kubernetesclient.KubernetesClientSingletons.instrumenter;

import io.kubernetes.client.openapi.ApiCallback;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.ApiResponse;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.List;
import java.util.Map;
import okhttp3.Request;

public class TracingApiCallback<T> implements ApiCallback<T> {
  private final ApiCallback<T> delegate;
  private final Context parentContext;
  private final Context context;
  private final Request request;

  public TracingApiCallback(
      ApiCallback<T> delegate, Context parentContext, Context context, Request request) {
    this.delegate = delegate;
    this.parentContext = parentContext;
    this.context = context;
    this.request = request;
  }

  @Override
  public void onFailure(ApiException e, int status, Map<String, List<String>> headers) {
    instrumenter().end(context, request, new ApiResponse<>(status, headers), e);
    if (delegate != null) {
      try (Scope ignored = parentContext.makeCurrent()) {
        delegate.onFailure(e, status, headers);
      }
    }
  }

  @Override
  public void onSuccess(T t, int status, Map<String, List<String>> headers) {
    instrumenter().end(context, request, new ApiResponse<>(status, headers), null);
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
