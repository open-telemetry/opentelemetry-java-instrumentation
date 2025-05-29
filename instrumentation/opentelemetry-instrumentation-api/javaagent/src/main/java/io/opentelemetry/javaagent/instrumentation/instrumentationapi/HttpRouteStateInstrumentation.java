/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationapi;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import application.io.opentelemetry.api.trace.Span;
import application.io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

final class HttpRouteStateInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("application.io.opentelemetry.instrumentation.api.internal.HttpRouteState");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("update")
            .and(takesArgument(0, named("application.io.opentelemetry.context.Context")))
            .and(takesArgument(1, int.class))
            .and(takesArgument(2, String.class)),
        this.getClass().getName() + "$UpdateAdvice");
    transformer.applyAdviceToMethod(
        named("updateSpan")
            .and(takesArgument(0, named("application.io.opentelemetry.context.Context")))
            .and(takesArgument(1, named("application.io.opentelemetry.api.trace.Span"))),
        this.getClass().getName() + "$UpdateSpanAdvice");
  }

  @SuppressWarnings("unused")
  public static class UpdateAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Context applicationContext,
        @Advice.Argument(1) int updatedBySourceOrder,
        @Advice.Argument(2) String route) {

      io.opentelemetry.context.Context agentContext =
          AgentContextStorage.getAgentContext(applicationContext);

      io.opentelemetry.instrumentation.api.internal.HttpRouteState agentRouteState =
          io.opentelemetry.instrumentation.api.internal.HttpRouteState.fromContextOrNull(
              agentContext);
      if (agentRouteState == null) {
        return;
      }

      agentRouteState.update(agentContext, updatedBySourceOrder, route);
    }
  }

  @SuppressWarnings("unused")
  public static class UpdateSpanAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Context applicationContext, @Advice.Argument(1) Span applicationSpan) {

      io.opentelemetry.context.Context agentContext =
          AgentContextStorage.getAgentContext(applicationContext);
      io.opentelemetry.instrumentation.api.internal.HttpRouteState.updateSpan(
          agentContext, Bridging.toAgentOrNull(applicationSpan));
    }
  }
}
