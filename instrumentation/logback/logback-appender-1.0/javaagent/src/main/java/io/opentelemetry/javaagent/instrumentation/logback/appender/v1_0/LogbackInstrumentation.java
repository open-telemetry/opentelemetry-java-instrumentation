/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.appender.v1_0;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.opentelemetry.instrumentation.api.appender.internal.LogEmitterProvider;
import io.opentelemetry.instrumentation.logback.appender.v1_0.internal.LoggingEventMapper;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import io.opentelemetry.javaagent.instrumentation.api.appender.internal.AgentLogEmitterProvider;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class LogbackInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("ch.qos.logback.classic.Logger");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("callAppenders"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("ch.qos.logback.classic.spi.ILoggingEvent"))),
        LogbackInstrumentation.class.getName() + "$CallAppendersAdvice");
  }

  @SuppressWarnings("unused")
  public static class CallAppendersAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(0) ILoggingEvent event,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {
      // need to track call depth across all loggers in order to avoid double capture when one
      // logging framework delegates to another
      callDepth = CallDepth.forClass(LogEmitterProvider.class);
      if (callDepth.getAndIncrement() == 0) {
        LoggingEventMapper.INSTANCE.emit(AgentLogEmitterProvider.get(), event);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(@Advice.Local("otelCallDepth") CallDepth callDepth) {
      callDepth.decrementAndGet();
    }
  }
}
