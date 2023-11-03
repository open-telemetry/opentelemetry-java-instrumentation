/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.classhook;

import static java.util.logging.Level.FINE;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.logging.Logger;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ClassHookInstrumentation implements TypeInstrumentation {
  private static final Logger logger = Logger.getLogger(ClassHookInstrumentation.class.getName());

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.azure.spring.cloud.test.config.client");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    logger.log(FINE, "Applying ClassHookMethodAdvice to {0}.", transformer.getClass());
    transformer.applyAdviceToMethod(
        isPublic(), this.getClass().getName() + "$ClassHookMethodAdvice");
  }

  @SuppressWarnings("unused")
  static class ClassHookMethodAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter() {
      logger.log(FINE, "Enter public function.");
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit() {
      logger.log(FINE, "Exit public function.");
    }
  }
}
