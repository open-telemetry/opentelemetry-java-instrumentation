/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.logging;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.javaagent.bootstrap.logging.ApplicationLoggerFlags;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class LoggingApplicationListenerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        // spring boot 1.x
        "org.springframework.boot.logging.LoggingApplicationListener",
        // spring boot 2.+
        "org.springframework.boot.context.logging.LoggingApplicationListener");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // the logger is properly initialized once this method exits
    transformer.applyAdviceToMethod(
        named("initialize"), this.getClass().getName() + "$InitializeAdvice");
  }

  @SuppressWarnings("unused")
  public static class InitializeAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit() {
      if (ApplicationLoggerFlags.bridgeSpringBootLogging()) {
        Slf4jApplicationLoggerBridge.install();
      }
    }
  }
}
