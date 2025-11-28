/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.extensions.inlined;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class SmokeInlinedInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.opentelemetry.smoketest.extensions.app.AppMain");
  }

  @Override
  public void transform(TypeTransformer typeTransformer) {
    typeTransformer.applyAdviceToMethod(
        named("returnValue").and(takesArgument(0, int.class)),
        this.getClass().getName() + "$ModifyReturnValueAdvice");
    typeTransformer.applyAdviceToMethod(
        named("methodArguments").and(takesArgument(0, int.class)),
        this.getClass().getName() + "$ModifyArgumentsAdvice");
  }

  @SuppressWarnings("unused")
  public static class ModifyReturnValueAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.Return(readOnly = false) int returnValue) {
      returnValue = returnValue + 1;
    }
  }

  @SuppressWarnings("unused")
  public static class ModifyArgumentsAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(value = 0, readOnly = false) int argument) {
      argument = argument - 1;
    }
  }
}
