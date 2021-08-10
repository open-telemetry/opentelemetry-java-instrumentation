package io.opentelemetry.instrumentation.okhttp.v3_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.caching.Cache;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.Timeout;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

class TracingCallFactory implements Call.Factory {
  private static final Cache<Request, Context> contextsByRequest = Cache.newBuilder().setWeakKeys().build();
  private final OkHttpClient okHttpClient;

  TracingCallFactory(OkHttpClient okHttpClient) {this.okHttpClient = okHttpClient;}

  @Nullable
  static Context getCallingContextForRequest(Request request) {
    return contextsByRequest.get(request);
  }

  @Override
  public Call newCall(Request request) {
    Context callingContext = Context.current();
    contextsByRequest.put(request, callingContext);
    return new TracingCall(okHttpClient.newCall(request), callingContext);
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
      try {
        Method clone = delegate.getClass().getDeclaredMethod("clone");
        return new TracingCall((Call) clone.invoke(delegate), Context.current());
      } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
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

    public Timeout timeout() {
      try {
        Method timeout = delegate.getClass().getMethod("timeout");
        return (Timeout) timeout.invoke(delegate);
      } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
        //do nothing...we're before 3.12, or something else has gone wrong that we can't do anything about.
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
