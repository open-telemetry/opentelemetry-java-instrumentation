/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jul;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import application.java.util.logging.Logger;
import io.opentelemetry.instrumentation.api.appender.LogEmitterProvider;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import java.util.logging.LogRecord;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class JavaUtilLoggingInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("application.java.util.logging.Logger"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("log"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("java.util.logging.LogRecord"))),
        JavaUtilLoggingInstrumentation.class.getName() + "$LogAdvice");
  }

  @SuppressWarnings("unused")
  public static class LogAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This final Logger logger,
        @Advice.Argument(0) final LogRecord logRecord,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {
      // need to track call depth across all loggers in order to avoid double capture when one
      // logging framework delegates to another
      callDepth = CallDepth.forClass(LogEmitterProvider.class);
      if (callDepth.getAndIncrement() == 0) {
        JavaUtilLoggingHelper.capture(logger, logRecord);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(@Advice.Local("otelCallDepth") CallDepth callDepth) {
      callDepth.decrementAndGet();
    }
  }
}
