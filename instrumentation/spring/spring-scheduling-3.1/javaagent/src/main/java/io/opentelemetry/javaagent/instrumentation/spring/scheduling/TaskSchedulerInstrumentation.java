/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.scheduling;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.bootstrap.spring.SpringSchedulingTaskTracing;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class TaskSchedulerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.springframework.scheduling.TaskScheduler"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        nameStartsWith("schedule").and(takesArgument(0, Runnable.class)),
        this.getClass().getName() + "$ScheduleMethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class ScheduleMethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onSchedule(@Advice.Argument(value = 0, readOnly = false) Runnable runnable) {
      if (SpringSchedulingTaskTracing.wrappingEnabled()) {
        runnable = SpringSchedulingRunnableWrapper.wrapIfNeeded(runnable);
      }
    }
  }
}
