package io.opentelemetry.auto.instrumentation.okhttp3;

import static io.opentelemetry.auto.instrumentation.okhttp3.OkHttpClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.okhttp3.RequestBuilderInjectAdapter.SETTER;
import static io.opentelemetry.trace.Span.Kind.CLIENT;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
public class TracingInterceptor implements Interceptor {
  public static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.okhttp-3.0");

  @Override
  public Response intercept(final Chain chain) throws IOException {
    final Span span = TRACER.spanBuilder("okhttp.request").setSpanKind(CLIENT).startSpan();

    try (final Scope scope = TRACER.withSpan(span)) {
      DECORATE.afterStart(span);
      DECORATE.onRequest(span, chain.request());

      final Request.Builder requestBuilder = chain.request().newBuilder();
      TRACER.getHttpTextFormat().inject(span.getContext(), requestBuilder, SETTER);

      final Response response;
      try {
        response = chain.proceed(requestBuilder.build());
      } catch (final Exception e) {
        DECORATE.onError(span, e);
        span.end();
        throw e;
      }

      DECORATE.onResponse(span, response);
      DECORATE.beforeFinish(span);
      span.end();
      return response;
    }
  }
}
