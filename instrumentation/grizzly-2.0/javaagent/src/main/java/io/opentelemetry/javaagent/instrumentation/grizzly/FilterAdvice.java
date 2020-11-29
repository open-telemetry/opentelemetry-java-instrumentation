/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import static io.opentelemetry.javaagent.instrumentation.grizzly.GrizzlyHttpServerTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import net.bytebuddy.asm.Advice;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;

public class FilterAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.This BaseFilter it,
      @Advice.Argument(0) FilterChainContext ctx,
      @Advice.Local("otelScope") Scope scope) {
    if (Java8BytecodeBridge.currentSpan().getSpanContext().isValid()) {
      return;
    }

    Context context = tracer().getServerContext(ctx);
    if (context != null) {
      scope = context.makeCurrent();
    }
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(@Advice.This BaseFilter it, @Advice.Local("otelScope") Scope scope) {
    if (scope != null) {
      scope.close();
    }
  }
}
