/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_7_0;

import static io.opentelemetry.javaagent.instrumentation.thrift.v0_7_0.ThriftSingletons.clientInstrumenter;
import static io.opentelemetry.javaagent.instrumentation.thrift.v0_7_0.ThriftSingletons.serverInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.thrift.common.RequestScopeContext;
import io.opentelemetry.instrumentation.thrift.common.ThriftRequest;
import org.apache.thrift.async.AsyncMethodCallback;

public final class AsyncMethodCallbackWrapper<T> implements AsyncMethodCallback<T> {
  private final AsyncMethodCallback<T> delegate;
  private RequestScopeContext requestScopeContext;
  private final boolean isServer;

  public AsyncMethodCallbackWrapper(AsyncMethodCallback<T> methodCallback, boolean isServer) {
    this.delegate = methodCallback;
    this.isServer = isServer;
  }

  public void setRequestScopeContext(RequestScopeContext requestScopeContext) {
    this.requestScopeContext = requestScopeContext;
  }

  @Override
  public void onComplete(T t) {
    try {
      if (this.requestScopeContext == null) {
        return;
      }
      this.requestScopeContext.close();
      Context context = this.requestScopeContext.getContext();
      ThriftRequest request = this.requestScopeContext.getRequest();
      if (isServer) {
        serverInstrumenter().end(context, request, 0, null);
      } else {
        clientInstrumenter().end(context, request, 0, null);
      }
    } finally {
      this.delegate.onComplete(t);
    }
  }

  @Override
  public void onError(Exception e) {
    try {
      if (this.requestScopeContext == null) {
        return;
      }
      this.requestScopeContext.close();
      Context context = this.requestScopeContext.getContext();
      ThriftRequest request = this.requestScopeContext.getRequest();
      if (isServer) {
        serverInstrumenter().end(context, request, 1, e);
      } else {
        clientInstrumenter().end(context, request, 1, e);
      }
    } finally {
      this.delegate.onError(e);
    }
  }
}
