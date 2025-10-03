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
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
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
    // Inline instrumentation to prevent problems with invokedynamic-recursion
    applyInlineAdvice(transformer, methodMatcher, this.getClass().getName() + "$LoadClassAdvice");
  }

  @SuppressWarnings("unused")
  public static class LoadClassAdvice {

    // Class loader stub is shaded back to the real class loader class. It is used to call protected
    // method from the advice that the complier won't let us call directly. During runtime it is
    // fine since this code is inlined into subclasses of ClassLoader that can call protected
    // methods.
    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static Class<?> onEnter(
        @Advice.This java.lang.ClassLoader classLoader,
        @Advice.This
            io.opentelemetry.javaagent.instrumentation.internal.classloader.stub.ClassLoader
                classLoaderStub,
        @Advice.Argument(0) String name) {
      InjectedClassHelper.HelperClassInfo helperClassInfo =
          InjectedClassHelper.getHelperClassInfo(classLoader, name);
      if (helperClassInfo != null) {
        Class<?> clazz = classLoaderStub.findLoadedClass(name);
        if (clazz != null) {
          return clazz;
        }
        try {
          byte[] bytes = helperClassInfo.getClassBytes();
          return classLoaderStub.defineClass(
              name, bytes, 0, bytes.length, helperClassInfo.getProtectionDomain());
        } catch (LinkageError error) {
          clazz = classLoaderStub.findLoadedClass(name);
          if (clazz != null) {
            return clazz;
          }
          throw error;
        }
      }
      return null;
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static Class<?> onExit(
        @Advice.Return Class<?> originalResult, @Advice.Enter Class<?> loadedClass) {
      return loadedClass != null ? loadedClass : originalResult;
    }
  }
}
