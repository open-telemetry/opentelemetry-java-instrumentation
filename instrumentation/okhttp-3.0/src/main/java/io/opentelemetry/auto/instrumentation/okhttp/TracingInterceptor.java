/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.okhttp;

import static io.opentelemetry.auto.instrumentation.okhttp.OkHttpClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.okhttp.RequestBuilderInjectAdapter.SETTER;
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
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.okhttp-3.0");

  @Override
  public Response intercept(final Chain chain) throws IOException {
    final Span span =
        TRACER
            .spanBuilder(DECORATE.spanNameForRequest(chain.request()))
            .setSpanKind(CLIENT)
            .startSpan();

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
