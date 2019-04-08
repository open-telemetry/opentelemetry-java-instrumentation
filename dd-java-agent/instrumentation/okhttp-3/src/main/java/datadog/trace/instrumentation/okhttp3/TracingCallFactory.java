package datadog.trace.instrumentation.okhttp3;

import static datadog.trace.instrumentation.okhttp3.OkHttpClientDecorator.DECORATE;
import static datadog.trace.instrumentation.okhttp3.OkHttpClientDecorator.NETWORK_DECORATE;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.util.GlobalTracer;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TracingCallFactory implements Call.Factory {

  private final OkHttpClient okHttpClient;

  public TracingCallFactory(final OkHttpClient okHttpClient) {
    this.okHttpClient = okHttpClient;
  }

  @Override
  public Call newCall(final Request request) {
    final Span span = GlobalTracer.get().buildSpan("okhttp.http").start();
    try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, false)) {
      DECORATE.afterStart(scope);
      DECORATE.onRequest(span, request);

      /** In case of exception network interceptor is not called */
      final OkHttpClient.Builder okBuilder = okHttpClient.newBuilder();
      okBuilder.networkInterceptors().add(0, new NetworkInterceptor(scope.span().context()));

      okBuilder
          .interceptors()
          .add(
              0,
              new Interceptor() {
                @Override
                public Response intercept(final Chain chain) throws IOException {
                  try (final Scope interceptorScope =
                      GlobalTracer.get().scopeManager().activate(span, false)) {
                    return chain.proceed(chain.request());
                  } catch (final Exception ex) {
                    DECORATE.onError(scope, ex);
                    throw ex;
                  } finally {
                    DECORATE.beforeFinish(span);
                    span.finish();
                  }
                }
              });
      return okBuilder.build().newCall(request);
    } catch (final Throwable ex) {
      // Not sure what would cause it to get here.
      DECORATE.onError(span, ex);
      throw new RuntimeException(ex);
    }
  }

  static class NetworkInterceptor implements Interceptor {
    public SpanContext parentContext;

    NetworkInterceptor(final SpanContext spanContext) {
      parentContext = spanContext;
    }

    @Override
    public Response intercept(final Chain chain) throws IOException {
      try (final Scope networkScope =
          GlobalTracer.get().buildSpan("okhttp.http").asChildOf(parentContext).startActive(true)) {
        NETWORK_DECORATE.afterStart(networkScope);
        NETWORK_DECORATE.onRequest(networkScope.span(), chain.request());
        NETWORK_DECORATE.onPeerConnection(
            networkScope.span(), chain.connection().socket().getInetAddress());

        final Request.Builder requestBuilder = chain.request().newBuilder();
        GlobalTracer.get()
            .inject(
                networkScope.span().context(),
                Format.Builtin.HTTP_HEADERS,
                new RequestBuilderInjectAdapter(requestBuilder));

        final Response response;
        try {
          response = chain.proceed(requestBuilder.build());
        } catch (final Exception e) {
          NETWORK_DECORATE.onError(networkScope, e);
          throw e;
        }

        NETWORK_DECORATE.onResponse(networkScope.span(), response);
        NETWORK_DECORATE.beforeFinish(networkScope);
        return response;
      }
    }
  }
}
