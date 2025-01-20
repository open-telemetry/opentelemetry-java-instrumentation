/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkaactor;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class AkkaScheduleInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("akka.actor.LightArrayRevolverScheduler");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("schedule")
            .and(takesArgument(0, named("scala.concurrent.duration.FiniteDuration")))
            .and(takesArgument(1, named("scala.concurrent.duration.FiniteDuration")))
            .and(takesArgument(2, named("java.lang.Runnable")))
            .and(takesArgument(3, named("scala.concurrent.ExecutionContext"))),
        AkkaScheduleInstrumentation.class.getName() + "$ScheduleAdvice");
    transformer.applyAdviceToMethod(
        named("scheduleOnce")
            .and(takesArgument(0, named("scala.concurrent.duration.FiniteDuration")))
            .and(takesArgument(1, named("java.lang.Runnable")))
            .and(takesArgument(2, named("scala.concurrent.ExecutionContext"))),
        AkkaScheduleInstrumentation.class.getName() + "$ScheduleOnceAdvice");
  }

  @SuppressWarnings("unused")
  public static class ScheduleAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enterSchedule(
        @Advice.Argument(value = 2, readOnly = false) Runnable runnable) {
      runnable = AkkaSchedulerTaskWrapper.wrap(runnable);
    }
  }

  @SuppressWarnings("unused")
  public static class ScheduleOnceAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enterScheduleOnce(
        @Advice.Argument(value = 1, readOnly = false) Runnable runnable) {
      runnable = AkkaSchedulerTaskWrapper.wrap(runnable);
    }
  }
}
