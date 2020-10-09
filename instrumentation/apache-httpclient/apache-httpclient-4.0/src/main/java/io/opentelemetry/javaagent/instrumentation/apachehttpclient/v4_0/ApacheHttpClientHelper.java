/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.apachehttpclient.v4_0;

import static io.opentelemetry.instrumentation.auto.apachehttpclient.v4_0.ApacheHttpClientTracer.TRACER;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.instrumentation.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;

public class ApacheHttpClientHelper {

  public static SpanWithScope doMethodEnter(HttpUriRequest request) {
    Span span = TRACER.startSpan(request);
    Scope scope = TRACER.startScope(span, request);
    return new SpanWithScope(span, scope);
  }

  public static void doMethodExitAndResetCallDepthThread(
      SpanWithScope spanWithScope, Object result, Throwable throwable) {
    if (spanWithScope == null) {
      return;
    }
    CallDepthThreadLocalMap.reset(HttpClient.class);

    doMethodExit(spanWithScope, result, throwable);
  }

  public static void doMethodExit(SpanWithScope spanWithScope, Object result, Throwable throwable) {
    try {
      Span span = spanWithScope.getSpan();
      if (result instanceof HttpResponse) {
        TRACER.onResponse(span, (HttpResponse) result);
      } // else they probably provided a ResponseHandler
      if (throwable != null) {
        TRACER.endExceptionally(span, throwable);
      } else {
        TRACER.end(span);
      }
    } finally {
      spanWithScope.closeScope();
    }
  }
}
