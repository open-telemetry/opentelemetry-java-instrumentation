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

package io.opentelemetry.auto.instrumentation.khttp;

import static io.opentelemetry.auto.instrumentation.khttp.KHttpDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.khttp.KHttpDecorator.TRACER;
import static io.opentelemetry.auto.instrumentation.khttp.KHttpHeadersInjectAdapter.SETTER;
import static io.opentelemetry.auto.instrumentation.khttp.KHttpHeadersInjectAdapter.asWritable;
import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.instrumentation.api.CallDepthThreadLocalMap;
import io.opentelemetry.instrumentation.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import java.util.Map;
import khttp.KHttp;
import khttp.responses.Response;
import net.bytebuddy.asm.Advice;

public class KHttpAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static SpanWithScope methodEnter(
      @Advice.Argument(value = 0) String method,
      @Advice.Argument(value = 1) String uri,
      @Advice.Argument(value = 2, readOnly = false) Map<String, String> headers) {

    int callDepth = CallDepthThreadLocalMap.incrementCallDepth(KHttp.class);
    if (callDepth > 0) {
      return null;
    }

    Span span = TRACER.spanBuilder("HTTP " + method).setSpanKind(CLIENT).startSpan();

    DECORATE.afterStart(span);
    DECORATE.onRequest(span, new RequestWrapper(method, uri, headers));

    Context context = withSpan(span, Context.current());

    headers = asWritable(headers);
    OpenTelemetry.getPropagators().getHttpTextFormat().inject(context, headers, SETTER);
    return new SpanWithScope(span, withScopedContext(context));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void methodExit(
      @Advice.Enter final SpanWithScope spanWithScope,
      @Advice.Return final Response result,
      @Advice.Thrown final Throwable throwable) {
    if (spanWithScope == null) {
      return;
    }
    CallDepthThreadLocalMap.reset(KHttp.class);

    try {
      Span span = spanWithScope.getSpan();

      DECORATE.onResponse(span, result);
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
      span.end();
    } finally {
      spanWithScope.closeScope();
    }
  }
}
