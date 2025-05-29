/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class PekkoScheduleInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.actor.LightArrayRevolverScheduler");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("schedule")
            .and(takesArgument(0, named("scala.concurrent.duration.FiniteDuration")))
            .and(takesArgument(1, named("scala.concurrent.duration.FiniteDuration")))
            .and(takesArgument(2, named("java.lang.Runnable")))
            .and(takesArgument(3, named("scala.concurrent.ExecutionContext"))),
        PekkoScheduleInstrumentation.class.getName() + "$ScheduleAdvice");
    transformer.applyAdviceToMethod(
        named("scheduleOnce")
            .and(takesArgument(0, named("scala.concurrent.duration.FiniteDuration")))
            .and(takesArgument(1, named("java.lang.Runnable")))
            .and(takesArgument(2, named("scala.concurrent.ExecutionContext"))),
        PekkoScheduleInstrumentation.class.getName() + "$ScheduleOnceAdvice");
  }

  @SuppressWarnings("unused")
  public static class ScheduleAdvice {

    @Advice.AssignReturned.ToArguments(@ToArgument(2))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Runnable enterSchedule(@Advice.Argument(value = 2) Runnable runnable) {
      return PekkoSchedulerTaskWrapper.wrap(runnable);
    }
  }

  @SuppressWarnings("unused")
  public static class ScheduleOnceAdvice {

    @Advice.AssignReturned.ToArguments(@ToArgument(1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Runnable enterScheduleOnce(@Advice.Argument(value = 1) Runnable runnable) {
      return PekkoSchedulerTaskWrapper.wrap(runnable);
    }
  }
}
