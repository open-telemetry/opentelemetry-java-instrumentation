/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.grizzly.client;

import static io.opentelemetry.instrumentation.auto.grizzly.client.GrizzlyClientTracer.TRACER;

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHandler;
import com.ning.http.client.Response;
import io.grpc.Context;
import io.opentelemetry.context.ContextUtils;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.Pair;
import io.opentelemetry.trace.Span;
import net.bytebuddy.asm.Advice;

public class GrizzlyClientResponseAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope onEnter(
      @Advice.This AsyncCompletionHandler<?> handler, @Advice.Argument(0) Response response) {

    // TODO I think all this should happen on exit, not on enter.
    // After response was handled by user provided handler.
    ContextStore<AsyncHandler, Pair> contextStore =
        InstrumentationContext.get(AsyncHandler.class, Pair.class);
    Pair<Context, Span> spanWithParent = contextStore.get(handler);
    if (null != spanWithParent) {
      contextStore.put(handler, null);
    }
    if (spanWithParent.hasRight()) {
      TRACER.end(spanWithParent.getRight(), response);
    }
    return spanWithParent.hasLeft()
        ? ContextUtils.withScopedContext(spanWithParent.getLeft())
        : null;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(@Advice.Enter Scope scope) {
    if (null != scope) {
      scope.close();
    }
  }
}
