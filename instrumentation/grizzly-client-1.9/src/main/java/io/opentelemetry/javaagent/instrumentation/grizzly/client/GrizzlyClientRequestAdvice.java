/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly.client;

import static io.opentelemetry.javaagent.instrumentation.grizzly.client.GrizzlyClientTracer.TRACER;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.Request;
import io.grpc.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.Pair;
import io.opentelemetry.trace.Span;
import net.bytebuddy.asm.Advice;

public class GrizzlyClientRequestAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope onEnter(
      @Advice.Argument(0) Request request, @Advice.Argument(1) AsyncHandler<?> handler) {
    Context parentContext = Context.current();

    Span span = TRACER.startSpan(request);
    InstrumentationContext.get(AsyncHandler.class, Pair.class)
        .put(handler, Pair.of(parentContext, span));
    return TRACER.startScope(span, request);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(@Advice.Enter Scope scope) {
    // span closed in ClientResponseAdvice
    scope.close();
  }
}
