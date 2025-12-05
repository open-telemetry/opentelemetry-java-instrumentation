/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.otellogging;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class LoggerFactoryInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.slf4j.LoggerFactory");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // once a call to getILoggerFactory() exits we can be certain that slf4j is properly initialized
    transformer.applyAdviceToMethod(
        named("getILoggerFactory").and(takesArguments(0)),
        this.getClass().getName() + "$GetLoggerFactoryAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetLoggerFactoryAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit() {
      Slf4jOtelLogger.install();
    }
  }
}
