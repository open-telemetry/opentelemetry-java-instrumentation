/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.v3_4.operator;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import application.io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ContextPropagationOperator34Instrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named(
        "application.io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(isStatic())
            .and(named("getOpenTelemetryContextFromContextView"))
            .and(takesArgument(0, named("reactor.util.context.ContextView")))
            .and(takesArgument(1, named("application.io.opentelemetry.context.Context")))
            .and(returns(named("application.io.opentelemetry.context.Context"))),
        ContextPropagationOperator34Instrumentation.class.getName() + "$GetContextViewAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetContextViewAdvice {
    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class)
    public static boolean methodEnter() {
      return false;
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) reactor.util.context.ContextView reactorContext,
        @Advice.Argument(1) Context defaultContext,
        @Advice.Return(readOnly = false) Context applicationContext) {

      io.opentelemetry.context.Context agentContext =
          ContextPropagationOperator.getOpenTelemetryContextFromContextView(reactorContext, null);
      if (agentContext == null) {
        applicationContext = defaultContext;
      } else {
        applicationContext = AgentContextStorage.toApplicationContext(agentContext);
      }
    }
  }
}
