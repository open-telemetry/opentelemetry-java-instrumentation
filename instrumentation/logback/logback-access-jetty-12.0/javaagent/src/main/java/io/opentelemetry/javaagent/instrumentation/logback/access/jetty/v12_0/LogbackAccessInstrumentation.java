/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.access.jetty.v12_0;

import static io.opentelemetry.javaagent.instrumentation.logback.access.jetty.v12_0.LogbackAccessSingletons.mapper;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import ch.qos.logback.access.common.spi.IAccessEvent;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class LogbackAccessInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("ch.qos.logback.core.spi.AppenderAttachableImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(named("appendLoopOnAppenders")).and(takesArguments(1)),
        LogbackAccessInstrumentation.class.getName() + "$CallAppendersAdvice");
  }

  @SuppressWarnings("unused")
  public static class CallAppendersAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(0) Object o, @Advice.Local("otelCallDepth") CallDepth callDepth) {
      if (o instanceof IAccessEvent event) {
        // need to track call depth across all loggers in order to avoid double capture when one
        // logging framework delegates to another
        callDepth = CallDepth.forClass(LoggerProvider.class);
        if (callDepth.getAndIncrement() == 0) {
          mapper().emit(GlobalOpenTelemetry.get().getLogsBridge(), event);
        }
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(@Advice.Local("otelCallDepth") CallDepth callDepth) {
      callDepth.decrementAndGet();
    }
  }
}
