/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
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
        // only instrumenting repeating jobs, not one-time scheduled jobs
        // (same behavior as ScheduledExecutorService)
        namedOneOf("scheduleAtFixedRate", "scheduleWithFixedDelay")
            .and(takesArgument(0, Runnable.class))
            .or(
                named("schedule")
                    .and(
                        takesArgument(0, Runnable.class)
                            .and(
                                takesArgument(
                                    // Trigger represents a repeating job
                                    1, named("org.springframework.scheduling.Trigger"))))),
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
