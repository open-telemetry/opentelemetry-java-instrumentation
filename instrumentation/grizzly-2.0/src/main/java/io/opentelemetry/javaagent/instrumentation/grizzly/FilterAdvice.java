/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.grizzly;

import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.instrumentation.auto.grizzly.GrizzlyHttpServerTracer.TRACER;

import io.grpc.Context;
import io.opentelemetry.context.Scope;
import net.bytebuddy.asm.Advice;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;

public class FilterAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.This BaseFilter it,
      @Advice.Argument(0) FilterChainContext ctx,
      @Advice.Local("otelScope") Scope scope) {
    if (TRACER.getCurrentSpan().getContext().isValid()) {
      return;
    }

    Context context = TRACER.getServerContext(ctx);
    if (context != null) {
      scope = withScopedContext(context);
    }
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(@Advice.This BaseFilter it, @Advice.Local("otelScope") Scope scope) {
    if (scope != null) {
      scope.close();
    }
  }
}
