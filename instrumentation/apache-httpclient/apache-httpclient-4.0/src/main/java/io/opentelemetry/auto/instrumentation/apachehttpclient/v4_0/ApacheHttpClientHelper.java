/*
 * Copyright The OpenTelemetry Authors
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

package io.opentelemetry.auto.instrumentation.apachehttpclient.v4_0;

import static io.opentelemetry.auto.instrumentation.apachehttpclient.v4_0.ApacheHttpClientDecorator.DECORATE;
import static io.opentelemetry.context.ContextUtils.withScopedContext;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.decorator.ClientDecorator;
import io.opentelemetry.instrumentation.auto.api.CallDepthThreadLocalMap;
import io.opentelemetry.instrumentation.auto.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;

public class ApacheHttpClientHelper {

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.spring-webflux-5.0");

  public static SpanWithScope doMethodEnter(final HttpUriRequest request) {
    return doMethodEnter(request, TRACER);
  }

  public static SpanWithScope doMethodEnter(final HttpUriRequest request, final Tracer tracer) {
    Span span = DECORATE.getOrCreateSpan(request, tracer);

    DECORATE.afterStart(span);
    DECORATE.onRequest(span, request);

    Context context = ClientDecorator.currentContextWith(span);
    if (span.getContext().isValid()) {
      DECORATE.inject(context, request);
    }
    Scope scope = withScopedContext(context);

    return new SpanWithScope(span, scope);
  }

  public static void doMethodExitAndResetCallDepthThread(
      final SpanWithScope spanWithScope, final Object result, final Throwable throwable) {
    if (spanWithScope == null) {
      return;
    }
    CallDepthThreadLocalMap.reset(HttpClient.class);

    doMethodExit(spanWithScope, result, throwable);
  }

  public static void doMethodExit(
      final SpanWithScope spanWithScope, final Object result, final Throwable throwable) {
    try {
      Span span = spanWithScope.getSpan();

      if (result instanceof HttpResponse) {
        DECORATE.onResponse(span, (HttpResponse) result);
      } // else they probably provided a ResponseHandler.

      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
      span.end();
    } finally {
      spanWithScope.closeScope();
    }
  }
}
