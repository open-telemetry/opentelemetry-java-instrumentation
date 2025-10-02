/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.classloader;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.instrumentation.internal.classloader.AdviceUtil.applyInlineAdvice;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.bootstrap.InjectedClassHelper;
import io.opentelemetry.javaagent.bootstrap.InjectedClassHelper.HelperClassLoader;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.invoke.MethodHandles;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * This instrumentation inserts loading of our injected helper classes at the start of {@code
 * ClassLoader.loadClass} method.
 */
public class LoadInjectedClassInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("java.lang.ClassLoader"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    ElementMatcher.Junction<MethodDescription> methodMatcher =
        isMethod()
            .and(named("loadClass"))
            .and(
                takesArguments(1)
                    .and(takesArgument(0, String.class))
                    .or(
                        takesArguments(2)
                            .and(takesArgument(0, String.class))
                            .and(takesArgument(1, boolean.class))))
            .and(isPublic().or(isProtected()))
            .and(not(isStatic()));
    boolean useLookup = Double.parseDouble(System.getProperty("java.specification.version")) >= 11;
    // Inline instrumentation to prevent problems with invokedynamic-recursion
    applyInlineAdvice(
        transformer,
        methodMatcher,
        this.getClass().getName()
            + (useLookup ? "$LoadClassAdvice" : "$LoadClassWithoutLookupAdvice"));
  }

  @SuppressWarnings("unused")
  public static class LoadClassAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static Class<?> onEnter(
        @Advice.This ClassLoader classLoader, @Advice.Argument(0) String name) throws Throwable {
      HelperClassLoader helperClassLoader =
          InjectedClassHelper.getHelperClassLoader(classLoader, name);
      return helperClassLoader != null
          ? helperClassLoader.loadHelperClass(MethodHandles.lookup())
          : null;
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static Class<?> onExit(
        @Advice.Return Class<?> originalResult, @Advice.Enter Class<?> loadedClass) {
      return loadedClass != null ? loadedClass : originalResult;
    }
  }

  @SuppressWarnings("unused")
  public static class LoadClassWithoutLookupAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static Class<?> onEnter(
        @Advice.This ClassLoader classLoader, @Advice.Argument(0) String name) throws Throwable {
      HelperClassLoader helperClassLoader =
          InjectedClassHelper.getHelperClassLoader(classLoader, name);
      // on jdk8 we can't use MethodHandles.lookup() because it fails when called from
      // java.lang.ClassLoader with java.lang.IllegalArgumentException: illegal lookupClass: class
      // java.lang.ClassLoader
      return helperClassLoader != null ? helperClassLoader.loadHelperClass(null) : null;
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static Class<?> onExit(
        @Advice.Return Class<?> originalResult, @Advice.Enter Class<?> loadedClass) {
      return loadedClass != null ? loadedClass : originalResult;
    }
  }
}
