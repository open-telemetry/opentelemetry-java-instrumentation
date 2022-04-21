/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.classloader;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.bootstrap.DefineClassHelper;
import io.opentelemetry.javaagent.bootstrap.DefineClassHelper.Handler.DefineClassContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.nio.ByteBuffer;
import java.security.ProtectionDomain;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class DefineClassInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("java.lang.ClassLoader");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("defineClass")
            .and(
                takesArguments(
                    String.class, byte[].class, int.class, int.class, ProtectionDomain.class)),
        DefineClassInstrumentation.class.getName() + "$DefineClassAdvice");
    transformer.applyAdviceToMethod(
        named("defineClass")
            .and(takesArguments(String.class, ByteBuffer.class, ProtectionDomain.class)),
        DefineClassInstrumentation.class.getName() + "$DefineClassAdvice2");
  }

  @SuppressWarnings("unused")
  public static class DefineClassAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static DefineClassContext onEnter(
        @Advice.This ClassLoader classLoader,
        @Advice.Argument(0) String className,
        @Advice.Argument(1) byte[] classBytes,
        @Advice.Argument(2) int offset,
        @Advice.Argument(3) int length) {
      return DefineClassHelper.beforeDefineClass(
          classLoader, className, classBytes, offset, length);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Enter DefineClassContext context) {
      DefineClassHelper.afterDefineClass(context);
    }
  }

  @SuppressWarnings("unused")
  public static class DefineClassAdvice2 {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static DefineClassContext onEnter(
        @Advice.This ClassLoader classLoader,
        @Advice.Argument(0) String className,
        @Advice.Argument(1) ByteBuffer classBytes) {
      return DefineClassHelper.beforeDefineClass(classLoader, className, classBytes);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Enter DefineClassContext context) {
      DefineClassHelper.afterDefineClass(context);
    }
  }
}
