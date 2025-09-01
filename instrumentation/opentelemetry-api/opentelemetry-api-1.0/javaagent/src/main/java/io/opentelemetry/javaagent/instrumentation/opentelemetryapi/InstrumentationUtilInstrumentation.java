/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import application.io.opentelemetry.context.Context;
import io.opentelemetry.api.internal.InstrumentationUtil;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class InstrumentationUtilInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("application.io.opentelemetry.api.internal.InstrumentationUtil");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("shouldSuppressInstrumentation")
            .and(takesArgument(0, named("application.io.opentelemetry.context.Context")))
            .and(returns(boolean.class)),
        this.getClass().getName() + "$ShouldSuppressAdvice");
    transformer.applyAdviceToMethod(
        named("suppressInstrumentation").and(takesArgument(0, Runnable.class)),
        this.getClass().getName() + "$SuppressAdvice");
  }

  @SuppressWarnings("unused")
  public static class ShouldSuppressAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, skipOn = Advice.OnNonDefaultValue.class)
    public static boolean methodEnter() {
      return true;
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(0) Context context, @Advice.Return(readOnly = false) boolean result) {
      result =
          InstrumentationUtil.shouldSuppressInstrumentation(
              AgentContextStorage.getAgentContext(context));
    }
  }

  @SuppressWarnings("unused")
  public static class SuppressAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, skipOn = Advice.OnNonDefaultValue.class)
    public static boolean methodEnter(@Advice.Argument(0) Runnable runnable) {
      InstrumentationUtil.suppressInstrumentation(runnable);
      return true;
    }
  }
}
