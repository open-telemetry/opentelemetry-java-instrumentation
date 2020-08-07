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

package io.opentelemetry.instrumentation.apachehttpclient.v4_0;

import static io.opentelemetry.instrumentation.apachehttpclient.v4_0.ApacheHttpClientTracer.TRACER;

import io.opentelemetry.auto.bootstrap.CallDepthThreadLocalMap;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;

public class ApacheHttpClientHelper {

  public static SpanWithScope doMethodEnter(final HttpUriRequest request) {
    Span span = TRACER.startSpan(request);
    Scope scope = TRACER.startScope(span, request);

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
      if (throwable != null) {
        TRACER.endExceptionally(span, (HttpResponse) result, throwable);
      } else {
        TRACER.end(span, (HttpResponse) result);
      }
    } finally {
      spanWithScope.closeScope();
    }
  }
}
