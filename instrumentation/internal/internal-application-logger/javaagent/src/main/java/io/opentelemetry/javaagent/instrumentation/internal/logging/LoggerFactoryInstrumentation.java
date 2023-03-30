/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.logging;

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
    transformer.applyAdviceToMethod(
        named("getILoggerFactory").and(takesArguments(0)),
        this.getClass().getName() + "$GetLoggerFactoryAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetLoggerFactoryAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit() {
      Slf4jApplicationLoggerBridge.install();
    }
  }
}
