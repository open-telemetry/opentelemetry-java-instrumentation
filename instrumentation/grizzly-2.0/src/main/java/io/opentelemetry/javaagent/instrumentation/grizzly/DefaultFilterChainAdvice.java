/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import static io.opentelemetry.javaagent.instrumentation.grizzly.GrizzlyHttpServerTracer.TRACER;

import io.opentelemetry.api.trace.Span;
import net.bytebuddy.asm.Advice;
import org.glassfish.grizzly.filterchain.FilterChainContext;

public class DefaultFilterChainAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onFail(
      @Advice.Argument(0) FilterChainContext ctx, @Advice.Argument(1) Throwable throwable) {
    Span span = TRACER.getServerSpan(ctx);
    if (span != null) {
      TRACER.endExceptionally(span, throwable);
    }
  }
}
