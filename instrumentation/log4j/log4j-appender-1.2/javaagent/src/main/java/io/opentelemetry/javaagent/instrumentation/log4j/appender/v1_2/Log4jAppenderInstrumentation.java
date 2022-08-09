/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.appender.v1_2;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.api.appender.internal.LogEmitterProvider;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.log4j.Category;
import org.apache.log4j.Priority;

class Log4jAppenderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.log4j.Category");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isProtected())
            .and(named("forcedLog"))
            .and(takesArguments(4))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, named("org.apache.log4j.Priority")))
            .and(takesArgument(2, Object.class))
            .and(takesArgument(3, Throwable.class)),
        Log4jAppenderInstrumentation.class.getName() + "$ForcedLogAdvice");
  }

  @SuppressWarnings("unused")
  public static class ForcedLogAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This Category logger,
        @Advice.Argument(1) Priority level,
        @Advice.Argument(2) Object message,
        @Advice.Argument(3) Throwable t,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {
      // need to track call depth across all loggers to avoid double capture when one logging
      // framework delegates to another
      callDepth = CallDepth.forClass(LogEmitterProvider.class);
      if (callDepth.getAndIncrement() == 0) {
        LogEventMapper.INSTANCE.capture(logger, level, message, t);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(@Advice.Local("otelCallDepth") CallDepth callDepth) {
      callDepth.decrementAndGet();
    }
  }
}
