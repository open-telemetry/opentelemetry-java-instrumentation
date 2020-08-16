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

package io.opentelemetry.instrumentation.auto.apachehttpclient.v4_0;

import static io.opentelemetry.instrumentation.auto.apachehttpclient.v4_0.ApacheHttpClientTracer.TRACER;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.auto.api.CallDepthThreadLocalMap;
import io.opentelemetry.instrumentation.auto.api.SpanWithScope;
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

  public static void doMethodExit(
      SpanWithScope spanWithScope, Object result, Throwable throwable) {
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
