/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_12;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.okhttp.v3_12.internal.OkHttpClientCallState;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.Timeout;

class TracingCallFactory implements Call.Factory {

  private final OkHttpClient okHttpClient;
  private final boolean captureTimings;

  TracingCallFactory(OkHttpClient okHttpClient, boolean captureTimings) {
    this.okHttpClient = okHttpClient;
    this.captureTimings = captureTimings;
  }

  @Override
  public Call newCall(Request request) {
    Context callingContext = Context.current();
    Request requestWithState =
        request
            .newBuilder()
            .tag(
                OkHttpClientCallState.class,
                new OkHttpClientCallState(callingContext, captureTimings))
            .build();
    return new TracingCall(okHttpClient.newCall(requestWithState), callingContext, this);
  }

  static class TracingCall implements Call {
    private final Call delegate;
    private final Context callingContext;
    private final TracingCallFactory factory;

    TracingCall(Call delegate, Context callingContext, TracingCallFactory factory) {
      this.delegate = delegate;
      this.callingContext = callingContext;
      this.factory = factory;
    }

    @Override
    public void cancel() {
      delegate.cancel();
    }

    @Override
    public Call clone() {
      // A cloned call is a separate execution, so route it back through the factory to give it its
      // own OkHttpClientCallState rather than sharing the original request's tag. We read the
      // current context inside newCall because cloning may happen in a different context than the
      // original call creation.
      return factory.newCall(delegate.request());
    }

    @Override
    public void enqueue(Callback callback) {
      delegate.enqueue(new TracingCallback(callback, callingContext));
    }

    @Override
    public Response execute() throws IOException {
      try (Scope ignored = callingContext.makeCurrent()) {
        return delegate.execute();
      }
    }

    @Override
    public boolean isCanceled() {
      return delegate.isCanceled();
    }

    @Override
    public boolean isExecuted() {
      return delegate.isExecuted();
    }

    @Override
    public Request request() {
      return delegate.request();
    }

    @Override
    public Timeout timeout() {
      return delegate.timeout();
    }

    private static class TracingCallback implements Callback {
      private final Callback delegate;
      private final Context callingContext;

      TracingCallback(Callback delegate, Context callingContext) {
        this.delegate = delegate;
        this.callingContext = callingContext;
      }

      @Override
      public void onFailure(Call call, IOException e) {
        try (Scope ignored = callingContext.makeCurrent()) {
          delegate.onFailure(call, e);
        }
      }

      @Override
      public void onResponse(Call call, Response response) throws IOException {
        try (Scope ignored = callingContext.makeCurrent()) {
          delegate.onResponse(call, response);
        }
      }
    }
  }
}
