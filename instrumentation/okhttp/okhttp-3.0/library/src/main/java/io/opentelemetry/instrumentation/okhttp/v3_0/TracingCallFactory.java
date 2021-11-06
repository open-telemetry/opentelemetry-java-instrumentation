/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.Timeout;

class TracingCallFactory implements Call.Factory {

  private static final VirtualField<Request, Context> contextsByRequest =
      VirtualField.find(Request.class, Context.class);

  // We use old-school reflection here, rather than MethodHandles because Android doesn't support
  // MethodHandles until API 26.
  @Nullable private static Method timeoutMethod;
  @Nullable private static Method cloneMethod;

  static {
    try {
      timeoutMethod = Call.class.getMethod("timeout");
    } catch (NoSuchMethodException e) {
      timeoutMethod = null;
    }
    try {
      cloneMethod = Call.class.getDeclaredMethod("clone");
    } catch (NoSuchMethodException e) {
      cloneMethod = null;
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
    Context callingContext = TracingInterceptor.TryInfo.newInfo(Context.current());
    Request requestCopy = request.newBuilder().build();
    contextsByRequest.set(requestCopy, callingContext);
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
      if (cloneMethod == null) {
        return (Call) super.clone();
      }
      try {
        // we pull the current context here, because the cloning might be happening in a different
        // context than the original call creation.
        return new TracingCall((Call) cloneMethod.invoke(delegate), Context.current());
      } catch (IllegalAccessException | InvocationTargetException e) {
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
      if (timeoutMethod == null) {
        return Timeout.NONE;
      }
      try {
        return (Timeout) timeoutMethod.invoke(delegate);
      } catch (IllegalAccessException | InvocationTargetException e) {
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
