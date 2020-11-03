/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.khttp;

import static io.opentelemetry.javaagent.instrumentation.khttp.KHttpHeadersInjectAdapter.asWritable;
import static io.opentelemetry.javaagent.instrumentation.khttp.KHttpTracer.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap.Depth;
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

    callDepth = tracer().getCallDepth();
    if (callDepth.getAndIncrement() == 0) {
      span = tracer().startSpan(new RequestWrapper(method, uri, headers));
      if (span.getSpanContext().isValid()) {
        headers = asWritable(headers);
        scope = tracer().startScope(span, headers);
      }
    }
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void methodExit(
      @Advice.Return Response response,
      @Advice.Thrown Throwable throwable,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope,
      @Advice.Local("otelCallDepth") Depth callDepth) {
    if (callDepth.decrementAndGet() == 0 && scope != null) {
      scope.close();
      if (throwable == null) {
        tracer().end(span, response);
      } else {
        tracer().endExceptionally(span, response, throwable);
      }
    }
  }
}
