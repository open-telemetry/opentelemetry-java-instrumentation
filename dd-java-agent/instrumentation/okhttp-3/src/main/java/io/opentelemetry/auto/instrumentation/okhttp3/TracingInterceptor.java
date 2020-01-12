package io.opentelemetry.auto.instrumentation.okhttp3;

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activateSpan;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.propagate;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.startSpan;
import static io.opentelemetry.auto.instrumentation.okhttp3.OkHttpClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.okhttp3.RequestBuilderInjectAdapter.SETTER;

import io.opentelemetry.auto.instrumentation.api.AgentScope;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
public class TracingInterceptor implements Interceptor {
  @Override
  public Response intercept(final Chain chain) throws IOException {
    final AgentSpan span = startSpan("okhttp.request");

    try (final AgentScope scope = activateSpan(span, true)) {
      DECORATE.afterStart(span);
      DECORATE.onRequest(span, chain.request());

      final Request.Builder requestBuilder = chain.request().newBuilder();
      propagate().inject(span, requestBuilder, SETTER);

      final Response response;
      try {
        response = chain.proceed(requestBuilder.build());
      } catch (final Exception e) {
        DECORATE.onError(span, e);
        throw e;
      }

      DECORATE.onResponse(span, response);
      DECORATE.beforeFinish(span);
      return response;
    }
  }
}
