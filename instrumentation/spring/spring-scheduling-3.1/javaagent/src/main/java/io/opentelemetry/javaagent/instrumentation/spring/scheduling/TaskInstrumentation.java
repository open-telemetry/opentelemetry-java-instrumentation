/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.scheduling;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class TaskInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.scheduling.config.Task");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, Runnable.class)),
        this.getClass().getName() + "$ConstructorAdvice");
  }

  public static class ConstructorAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onConstruction(
        @Advice.Argument(value = 0, readOnly = false) Runnable runnable) {
      runnable = SpringSchedulingRunnableWrapper.wrapIfNeeded(runnable);
    }
  }
}
