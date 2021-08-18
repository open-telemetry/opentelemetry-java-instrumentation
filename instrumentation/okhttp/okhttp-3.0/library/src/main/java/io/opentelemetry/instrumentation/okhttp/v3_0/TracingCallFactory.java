/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.caching.Cache;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.Timeout;
import org.checkerframework.checker.nullness.qual.Nullable;

class TracingCallFactory implements Call.Factory {
  private static final Cache<Request, Context> contextsByRequest =
      Cache.newBuilder().setWeakKeys().build();

  @Nullable private static MethodHandle timeoutMethodHandle;
  @Nullable private static MethodHandle cloneMethodHandle;

  static {
    MethodHandles.Lookup lookup = MethodHandles.publicLookup();
    try {
      MethodType methodType = MethodType.methodType(Timeout.class);
      timeoutMethodHandle = lookup.findVirtual(Call.class, "timeout", methodType);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      timeoutMethodHandle = null;
    }
    try {
      MethodType methodType = MethodType.methodType(Call.class);
      cloneMethodHandle = lookup.findVirtual(Call.class, "clone", methodType);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      cloneMethodHandle = null;
    }
  }

  private final OkHttpClient okHttpClient;

  TracingCallFactory(OkHttpClient okHttpClient) {
    this.okHttpClient = okHttpClient;
  }

  @Nullable
  static Context getCallingContextForRequest(Request request) {
    return contextsByRequest.get(request);
  }

  @Override
  public Call newCall(Request request) {
    Context callingContext = Context.current();
    Request requestCopy = request.newBuilder().build();
    contextsByRequest.put(requestCopy, callingContext);
    return new TracingCall(okHttpClient.newCall(requestCopy), callingContext);
  }

  static class TracingCall implements Call {
    private final Call delegate;
    private final Context callingContext;

    TracingCall(Call delegate, Context callingContext) {
      this.delegate = delegate;
      this.callingContext = callingContext;
    }

    @Override
    public void cancel() {
      delegate.cancel();
    }

    @Override
    public Call clone() throws CloneNotSupportedException {
      if (cloneMethodHandle == null) {
        return (Call) super.clone();
      }
      try {
        // we pull the current context here, because the cloning might be happening in a different
        // context than the original call creation.
        return new TracingCall((Call) cloneMethodHandle.invoke(delegate), Context.current());
      } catch (Throwable e) {
        return (Call) super.clone();
      }
    }

    @Override
    public void enqueue(Callback callback) {
      delegate.enqueue(new TracingCallback(callback, callingContext));
    }

    @Override
    public Response execute() throws IOException {
      try (Scope scope = callingContext.makeCurrent()) {
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

    // @Override method was introduced in 3.12
    public Timeout timeout() {
      if (timeoutMethodHandle == null) {
        return Timeout.NONE;
      }
      try {
        return (Timeout) timeoutMethodHandle.invoke(delegate);
      } catch (Throwable e) {
        // do nothing...we're before 3.12, or something else has gone wrong that we can't do
        // anything about.
        return Timeout.NONE;
      }
    }

    private static class TracingCallback implements Callback {
      private final Callback delegate;
      private final Context callingContext;

      public TracingCallback(Callback delegate, Context callingContext) {
        this.delegate = delegate;
        this.callingContext = callingContext;
      }

      @Override
      public void onFailure(Call call, IOException e) {
        try (Scope scope = callingContext.makeCurrent()) {
          delegate.onFailure(call, e);
        }
      }

      @Override
      public void onResponse(Call call, Response response) throws IOException {
        try (Scope scope = callingContext.makeCurrent()) {
          delegate.onResponse(call, response);
        }
      }
    }
  }
}
