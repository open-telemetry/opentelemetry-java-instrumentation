/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.reflection;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ReflectionInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf("jdk.internal.reflect.Reflection", "sun.reflect.Reflection");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("filterFields"))
            .and(takesArguments(2))
            .and(takesArgument(0, Class.class))
            .and(takesArgument(1, Field[].class))
            .and(isPublic())
            .and(isStatic()),
        ReflectionInstrumentation.class.getName() + "$FilterFieldsAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("filterMethods"))
            .and(takesArguments(2))
            .and(takesArgument(0, Class.class))
            .and(takesArgument(1, Method[].class))
            .and(isPublic())
            .and(isStatic()),
        ReflectionInstrumentation.class.getName() + "$FilterMethodsAdvice");
  }

  @SuppressWarnings("unused")
  public static class FilterFieldsAdvice {

    // using AsScalar is needed to return the array itself instead of "advice Object[] return"
    @Advice.AssignReturned.ToReturned
    @Advice.AssignReturned.AsScalar
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static Field[] filter(
        @Advice.Argument(0) Class<?> containingClass, @Advice.Return Field[] fields) {
      return ReflectionHelper.filterFields(containingClass, fields);
    }
  }

  @SuppressWarnings("unused")
  public static class FilterMethodsAdvice {

    // using AsScalar is needed to return the array itself instead of "advice Object[] return"
    @Advice.AssignReturned.ToReturned
    @Advice.AssignReturned.AsScalar
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static Method[] filter(
        @Advice.Argument(0) Class<?> containingClass, @Advice.Return Method[] methods) {
      return ReflectionHelper.filterMethods(containingClass, methods);
    }
  }
}
