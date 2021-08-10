package io.opentelemetry.instrumentation.okhttp.v4_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.Timeout;

class TracingCallFactory implements Call.Factory {
  private final OkHttpClient okHttpClient;

  TracingCallFactory(OkHttpClient okHttpClient) {this.okHttpClient = okHttpClient;}

  @Override
  public Call newCall(Request request) {
    Context callingContext = Context.current();
    Request taggedRequest = request.newBuilder()
        .tag(Context.class, callingContext)
        .build();
    return new TracingCall(okHttpClient.newCall(taggedRequest), callingContext);
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

    @Override
    public Timeout timeout() {
      return delegate.timeout();
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
