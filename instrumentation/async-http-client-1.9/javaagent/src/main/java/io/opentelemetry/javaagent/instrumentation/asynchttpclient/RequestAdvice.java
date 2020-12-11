/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.asynchttpclient.AsyncHttpClientTracer.tracer;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.Request;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.context.ContextWithParent;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import net.bytebuddy.asm.Advice;

public class RequestAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Argument(0) Request request,
      @Advice.Argument(1) AsyncHandler<?> handler,
      @Advice.Local("otelScope") Scope scope) {
    Context parentContext = currentContext();
    if (!tracer().shouldStartOperation(parentContext)) {
      return;
    }
    Context context = tracer().startOperation(parentContext, request);
    InstrumentationContext.get(AsyncHandler.class, ContextWithParent.class)
        .put(handler, new ContextWithParent(context, parentContext));
    scope = context.makeCurrent();
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(@Advice.Local("otelScope") Scope scope) {
    if (scope == null) {
      return;
    }
    scope.close();
    // span ended in ClientResponseAdvice
  }
}
