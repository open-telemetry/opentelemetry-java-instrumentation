/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
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
  private static final boolean supportsTags = supportsTags();

  // We use old-school reflection here, rather than MethodHandles because Android doesn't support
  // MethodHandles until API 26.
  @Nullable private static Method timeoutMethod;

  static {
    try {
      timeoutMethod = Call.class.getMethod("timeout");
    } catch (NoSuchMethodException e) {
      timeoutMethod = null;
    }
  }

  private final OkHttpClient okHttpClient;

  TracingCallFactory(OkHttpClient okHttpClient) {
    this.okHttpClient = okHttpClient;
  }

  // tags with class type are supported since OkHttp 3.11.0
  private static boolean supportsTags() {
    try {
      Request.class.getMethod("tag", Class.class);
      Request.Builder.class.getMethod("tag", Class.class, Object.class);
      return true;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  @Nullable
  static Context getCallingContextForRequest(Request request) {
    if (supportsTags) {
      return getContextFromRequestTag(request);
    }
    return contextsByRequest.get(request);
  }

  @NoMuzzle
  private static Context getContextFromRequestTag(Request request) {
    return request.tag(Context.class);
  }

  @Override
  public Call newCall(Request request) {
    Context callingContext = Context.current();
    Request requestWithContext = attachContextToRequest(request, callingContext);
    return new TracingCall(okHttpClient.newCall(requestWithContext), callingContext);
  }

  private static Request attachContextToRequest(Request request, Context context) {
    Request.Builder builder = request.newBuilder();
    if (supportsTags) {
      setContextToRequestTag(builder, context);
    }
    Request newRequest = builder.build();
    if (!supportsTags) {
      contextsByRequest.set(newRequest, context);
    }
    return newRequest;
  }

  @NoMuzzle
  private static void setContextToRequestTag(Request.Builder builder, Context context) {
    builder.tag(Context.class, context);
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
    public Call clone() {
      // we pull the current context here, because the cloning might be happening in a different
      // context than the original call creation.
      return new TracingCall(delegate.clone(), Context.current());
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

      TracingCallback(Callback delegate, Context callingContext) {
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
