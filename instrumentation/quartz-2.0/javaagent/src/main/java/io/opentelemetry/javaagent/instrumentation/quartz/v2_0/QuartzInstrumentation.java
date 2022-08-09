/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.quartz.v2_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.quartz.Scheduler;

public class QuartzInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.quartz.Scheduler");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named("org.quartz.Scheduler"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor(), this.getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void trackCallDepth(@Advice.Local("otelCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(Scheduler.class);
      callDepth.getAndIncrement();
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addTracingInterceptor(
        @Advice.This Scheduler scheduler, @Advice.Local("otelCallDepth") CallDepth callDepth) {
      // No-args constructor is automatically called by constructors with args, but we only want to
      // run once from the constructor with args because that is where the dedupe needs to happen.
      if (callDepth.decrementAndGet() > 0) {
        return;
      }
      QuartzSingletons.TELEMETRY.configure(scheduler);
    }
  }
}
