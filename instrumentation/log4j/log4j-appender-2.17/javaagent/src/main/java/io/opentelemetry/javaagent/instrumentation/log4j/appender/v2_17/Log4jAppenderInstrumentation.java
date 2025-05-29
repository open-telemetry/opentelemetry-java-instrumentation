/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.appender.v2_17;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.message.Message;

class Log4jAppenderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("org.apache.logging.log4j.spi.AbstractLogger"));
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.logging.log4j.spi.AbstractLogger");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isProtected().or(isPublic()))
            .and(named("log"))
            .and(takesArguments(6))
            .and(takesArgument(0, named("org.apache.logging.log4j.Level")))
            .and(takesArgument(1, named("org.apache.logging.log4j.Marker")))
            .and(takesArgument(2, String.class))
            .and(takesArgument(3, StackTraceElement.class))
            .and(takesArgument(4, named("org.apache.logging.log4j.message.Message")))
            .and(takesArgument(5, Throwable.class)),
        Log4jAppenderInstrumentation.class.getName() + "$LogAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isProtected().or(isPublic()))
            .and(named("logMessage"))
            .and(takesArguments(5))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, named("org.apache.logging.log4j.Level")))
            .and(takesArgument(2, named("org.apache.logging.log4j.Marker")))
            .and(takesArgument(3, named("org.apache.logging.log4j.message.Message")))
            .and(takesArgument(4, Throwable.class)),
        Log4jAppenderInstrumentation.class.getName() + "$LogMessageAdvice");
  }

  @SuppressWarnings("unused")
  public static class LogAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This Logger logger,
        @Advice.Argument(0) Level level,
        @Advice.Argument(1) Marker marker,
        @Advice.Argument(2) String loggerClassName,
        @Advice.Argument(3) StackTraceElement location,
        @Advice.Argument(4) Message message,
        @Advice.Argument(5) Throwable t,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {
      // need to track call depth across all loggers in order to avoid double capture when one
      // logging framework delegates to another
      callDepth = CallDepth.forClass(LoggerProvider.class);
      if (callDepth.getAndIncrement() == 0) {
        Log4jHelper.capture(logger, loggerClassName, location, level, marker, message, t);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(@Advice.Local("otelCallDepth") CallDepth callDepth) {
      callDepth.decrementAndGet();
    }
  }

  @SuppressWarnings("unused")
  public static class LogMessageAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This Logger logger,
        @Advice.Argument(0) String loggerClassName,
        @Advice.Argument(1) Level level,
        @Advice.Argument(2) Marker marker,
        @Advice.Argument(3) Message message,
        @Advice.Argument(4) Throwable t,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {
      // need to track call depth across all loggers in order to avoid double capture when one
      // logging framework delegates to another
      callDepth = CallDepth.forClass(LoggerProvider.class);
      if (callDepth.getAndIncrement() == 0) {
        Log4jHelper.capture(logger, loggerClassName, null, level, marker, message, t);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(@Advice.Local("otelCallDepth") CallDepth callDepth) {
      callDepth.decrementAndGet();
    }
  }
}
