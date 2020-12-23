/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty;

import static io.opentelemetry.javaagent.instrumentation.liberty.LibertyHttpServerTracer.tracer;

import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class LibertyUpdateSpanAdvice {

  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void onExit() {
    ThreadLocalContext ctx = ThreadLocalContext.get();
    if (ctx == null || !ctx.updateSpan()) {
      return;
    }

    tracer().updateSpanName(ctx.getRequest());
  }
}
