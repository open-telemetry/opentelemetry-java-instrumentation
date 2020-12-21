/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty;

import static io.opentelemetry.javaagent.instrumentation.liberty.LibertyHttpServerTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class LibertyStartSpanAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter() {
    ThreadLocalContext ctx = ThreadLocalContext.get();
    if (ctx == null || !ctx.startSpan()) {
      return;
    }

    Context context = tracer().startSpan(ctx.getRequest());
    Scope scope = context.makeCurrent();

    ctx.setContext(context);
    ctx.setScope(scope);
  }
}
