/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.bootstrap.spring.SpringSchedulingTaskTracing;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class TaskSchedulerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // we're only instrumenting the "real" scheduler implementations, and skipping all the decorator
    // impls
    return namedOneOf(
        "org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler",
        "org.springframework.scheduling.concurrent.ConcurrentTaskScheduler",
        "org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler",
        "org.springframework.scheduling.commonj.TimerManagerTaskScheduler");
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

    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Runnable onSchedule(@Advice.Argument(0) Runnable originalRunnable) {
      Runnable runnable = originalRunnable;
      if (SpringSchedulingTaskTracing.wrappingEnabled()) {
        runnable = SpringSchedulingRunnableWrapper.wrapIfNeeded(runnable);
      }
      return runnable;
    }
  }
}
