/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly.client;

import static io.opentelemetry.javaagent.instrumentation.grizzly.client.GrizzlyClientTracer.tracer;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.Request;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.api.Pair;
import net.bytebuddy.asm.Advice;

public class GrizzlyClientRequestAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope onEnter(
      @Advice.Argument(0) Request request, @Advice.Argument(1) AsyncHandler<?> handler) {
    Context parentContext = Java8BytecodeBridge.currentContext();

    Span span = tracer().startSpan(request);
    InstrumentationContext.get(AsyncHandler.class, Pair.class)
        .put(handler, Pair.of(parentContext, span));
    return tracer().startScope(span, request);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(@Advice.Enter Scope scope) {
    // span closed in ClientResponseAdvice
    scope.close();
  }
}
