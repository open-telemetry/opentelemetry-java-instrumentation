/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations.v1_16;

import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class CountedInstrumentation implements TypeInstrumentation {

  private final ElementMatcher.Junction<MethodDescription> annotatedMethodMatcher;
  private final ElementMatcher.Junction<MethodDescription> excludedMethodsMatcher;

  CountedInstrumentation() {
    annotatedMethodMatcher =
        isMethod()
            .and(
                isAnnotatedWith(
                    named("application.io.opentelemetry.instrumentation.annotations.Counted")));
    excludedMethodsMatcher = AnnotationExcludedMethods.configureExcludedMethods();
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return declaresMethod(annotatedMethodMatcher);
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        annotatedMethodMatcher.and(not(excludedMethodsMatcher)),
        getClass().getName() + "$CountedAdvice");
  }

  @SuppressWarnings("unused")
  public static class CountedAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Origin Method method) {
      CountedSingletons.increment(method);
    }
  }
}
