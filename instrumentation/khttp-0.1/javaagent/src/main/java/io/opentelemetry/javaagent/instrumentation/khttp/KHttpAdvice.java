/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.khttp;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.khttp.KHttpHeadersInjectAdapter.asWritable;
import static io.opentelemetry.javaagent.instrumentation.khttp.KHttpTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import java.util.Map;
import khttp.responses.Response;
import net.bytebuddy.asm.Advice;

public class KHttpAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void methodEnter(
      @Advice.Argument(value = 0) String method,
      @Advice.Argument(value = 1) String uri,
      @Advice.Argument(value = 2, readOnly = false) Map<String, String> headers,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope,
      @Advice.Local("otelCallDepth") CallDepth callDepth) {
    callDepth = tracer().getCallDepth();
    if (callDepth.getAndIncrement() != 0) {
      return;
    }
    Context parentContext = currentContext();
    if (!tracer().shouldStartSpan(parentContext)) {
      return;
    }

    headers = asWritable(headers);
    context = tracer().startSpan(parentContext, new RequestWrapper(method, uri, headers), headers);
    scope = context.makeCurrent();
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void methodExit(
      @Advice.Return Response response,
      @Advice.Thrown Throwable throwable,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope,
      @Advice.Local("otelCallDepth") CallDepth callDepth) {
    if (callDepth.decrementAndGet() == 0 && scope != null) {
      scope.close();
      if (throwable == null) {
        tracer().end(context, response);
      } else {
        tracer().endExceptionally(context, response, throwable);
      }
    }
  }
}
