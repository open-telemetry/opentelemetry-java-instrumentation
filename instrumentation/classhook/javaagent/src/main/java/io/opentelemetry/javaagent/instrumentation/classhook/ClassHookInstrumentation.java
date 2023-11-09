/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.classhook;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.logging.Level.FINE;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
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
    return named("com.tests.springboot.controller.WebController");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.tests.springboot.controller.WebController");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    logger.log(FINE, "transform apply ClassHookMethodAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(named("greeting")),
        this.getClass().getName() + "$ClassHookMethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class ClassHookMethodAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.Return(readOnly = false) String resp) {
      resp = "Hi! from ClassHookMethodAdvice -- " + resp;
    }
  }
}
