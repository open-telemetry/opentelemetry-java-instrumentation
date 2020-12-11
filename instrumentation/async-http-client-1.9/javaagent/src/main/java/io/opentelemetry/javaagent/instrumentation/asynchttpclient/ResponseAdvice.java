/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient;

import static io.opentelemetry.javaagent.instrumentation.asynchttpclient.AsyncHttpClientTracer.tracer;

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHandler;
import com.ning.http.client.Response;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.context.ContextWithParent;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import net.bytebuddy.asm.Advice;

public class ResponseAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope onEnter(
      @Advice.This AsyncCompletionHandler<?> handler, @Advice.Argument(0) Response response) {

    @SuppressWarnings("rawtypes")
    ContextStore<AsyncHandler, ContextWithParent> contextStore =
        InstrumentationContext.get(AsyncHandler.class, ContextWithParent.class);
    ContextWithParent contextWithParent = contextStore.get(handler);
    if (contextWithParent == null) {
      return Scope.noop();
    }
    contextStore.put(handler, null);
    tracer().end(contextWithParent.getContext(), response);
    return contextWithParent.getParentContext().makeCurrent();
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(@Advice.Enter Scope scope) {
    scope.close();
  }
}
