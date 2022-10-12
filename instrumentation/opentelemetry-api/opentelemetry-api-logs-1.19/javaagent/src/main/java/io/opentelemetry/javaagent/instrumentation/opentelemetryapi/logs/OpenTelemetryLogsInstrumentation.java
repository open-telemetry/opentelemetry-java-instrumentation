/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.logs;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.logs.bridge.ApplicationLoggerProvider;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

// Our convention for accessing agent package
@SuppressWarnings("UnnecessarilyFullyQualified")
public class OpenTelemetryLogsInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("application.io.opentelemetry.api.logs.GlobalLoggerProvider");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isStatic()).and(named("get")).and(takesArguments(0)),
        OpenTelemetryLogsInstrumentation.class.getName() + "$GetGlobalLogsAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetGlobalLogsAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class)
    public static Object onEnter() {
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return(readOnly = false)
            application.io.opentelemetry.api.logs.LoggerProvider loggerProvider) {
      loggerProvider = ApplicationLoggerProvider.INSTANCE;
    }
  }
}
