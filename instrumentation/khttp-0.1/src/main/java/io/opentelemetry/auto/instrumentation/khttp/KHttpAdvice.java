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

import static io.opentelemetry.auto.instrumentation.khttp.KHttpHeadersInjectAdapter.SETTER;
import static io.opentelemetry.auto.instrumentation.khttp.KHttpHeadersInjectAdapter.asWritable;
import static io.opentelemetry.auto.instrumentation.khttp.KHttpTracer.TRACER;
import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.auto.api.CallDepthThreadLocalMap.Depth;
import io.opentelemetry.trace.Span;
import java.util.Map;
import khttp.responses.Response;
import net.bytebuddy.asm.Advice;

public class KHttpAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void methodEnter(
      @Advice.Argument(value = 0) String method,
      @Advice.Argument(value = 1) String uri,
      @Advice.Argument(value = 2, readOnly = false) Map<String, String> headers,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope,
      @Advice.Local("otelCallDepth") Depth callDepth) {

    callDepth = TRACER.getCallDepth();
    if (callDepth.getAndIncrement() == 0) {
      span = TRACER.startSpan(new RequestWrapper(method, uri, headers));
      if (span.getContext().isValid()) {
        Context context = withSpan(span, Context.current());
        headers = asWritable(headers);
        OpenTelemetry.getPropagators().getHttpTextFormat().inject(context, headers, SETTER);
        scope = withScopedContext(context);
      }
    }
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void methodExit(
      @Advice.Return final Response response,
      @Advice.Thrown final Throwable throwable,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope,
      @Advice.Local("otelCallDepth") Depth callDepth) {
    if (callDepth.decrementAndGet() == 0 && scope != null) {
      scope.close();
      if (throwable == null) {
        TRACER.end(span, response);
      } else {
        TRACER.endExceptionally(span, response, throwable);
      }
    }
  }
}
