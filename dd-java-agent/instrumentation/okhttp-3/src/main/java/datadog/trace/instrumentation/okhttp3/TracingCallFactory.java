package datadog.trace.instrumentation.okhttp3;

import io.opentracing.Scope;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import okhttp3.Call;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * OkHttp client instrumentation.
 *
 * @author Pavol Loffay
 */
public class TracingCallFactory implements Call.Factory {
  static final String COMPONENT_NAME = "okhttp";

  private final OkHttpClient okHttpClient;

  private final Tracer tracer;
  private final List<OkHttpClientSpanDecorator> decorators;

  public TracingCallFactory(final OkHttpClient okHttpClient, final Tracer tracer) {
    this(okHttpClient, tracer, Collections.singletonList(OkHttpClientSpanDecorator.STANDARD_TAGS));
  }

  public TracingCallFactory(
      final OkHttpClient okHttpClient,
      final Tracer tracer,
      final List<OkHttpClientSpanDecorator> decorators) {
    this.okHttpClient = okHttpClient;
    this.tracer = tracer;
    this.decorators = new ArrayList<>(decorators);
  }

  @Override
  public Call newCall(final Request request) {
    Scope scope = null;
    try {
      scope =
          tracer
              .buildSpan("http.request")
              .withTag(Tags.COMPONENT.getKey(), COMPONENT_NAME)
              .startActive(false);

      /** In case of exception network interceptor is not called */
      final OkHttpClient.Builder okBuilder = okHttpClient.newBuilder();
      okBuilder
          .networkInterceptors()
          .add(0, new NetworkInterceptor(tracer, scope.span().context(), decorators));

      final Scope finalScope = scope;
      okBuilder
          .interceptors()
          .add(
              0,
              new Interceptor() {
                @Override
                public Response intercept(final Chain chain) throws IOException {
                  final Scope activeInterceptorSpan =
                      tracer.scopeManager().activate(finalScope.span(), true);
                  try {
                    return chain.proceed(chain.request());
                  } catch (final Exception ex) {
                    for (final OkHttpClientSpanDecorator spanDecorator : decorators) {
                      spanDecorator.onError(ex, activeInterceptorSpan.span());
                    }
                    throw ex;
                  } finally {
                    activeInterceptorSpan.close();
                  }
                }
              });
      return okBuilder.build().newCall(request);
    } catch (final Throwable ex) {
      for (final OkHttpClientSpanDecorator spanDecorator : decorators) {
        spanDecorator.onError(ex, scope.span());
      }
      throw new RuntimeException(ex);
    } finally {
      scope.close();
    }
  }

  static class NetworkInterceptor implements Interceptor {
    public SpanContext parentContext;
    public Tracer tracer;
    public List<OkHttpClientSpanDecorator> decorators;

    NetworkInterceptor(
        final Tracer tracer,
        final SpanContext spanContext,
        final List<OkHttpClientSpanDecorator> decorators) {
      this.parentContext = spanContext;
      this.tracer = tracer;
      this.decorators = decorators;
    }

    @Override
    public Response intercept(final Chain chain) throws IOException {
      try (Scope networkScope =
          tracer
              .buildSpan("http.request")
              .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
              .asChildOf(parentContext)
              .startActive(true)) {

        for (final OkHttpClientSpanDecorator spanDecorator : decorators) {
          spanDecorator.onRequest(chain.request(), networkScope.span());
        }

        final Request.Builder requestBuilder = chain.request().newBuilder();
        tracer.inject(
            networkScope.span().context(),
            Format.Builtin.HTTP_HEADERS,
            new RequestBuilderInjectAdapter(requestBuilder));
        final Response response = chain.proceed(requestBuilder.build());

        for (final OkHttpClientSpanDecorator spanDecorator : decorators) {
          spanDecorator.onResponse(chain.connection(), response, networkScope.span());
        }

        return response;
      }
    }
  }
}
