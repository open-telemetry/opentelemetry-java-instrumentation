/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.extensions.inlined;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Arrays;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class SmokeInlinedInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.opentelemetry.smoketest.extensions.app.AppMain");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("returnValue").and(takesArgument(0, int.class)),
        this.getClass().getName() + "$ModifyReturnValueAdvice");
    transformer.applyAdviceToMethod(
        named("methodArguments").and(takesArgument(0, int.class)),
        this.getClass().getName() + "$ModifyArgumentsAdvice");
    transformer.applyAdviceToMethod(
        named("setVirtualFieldValue")
            .and(takesArgument(0, Object.class))
            .and(takesArgument(1, Integer.class)),
        this.getClass().getName() + "$VirtualFieldSetAdvice");
    transformer.applyAdviceToMethod(
        named("getVirtualFieldValue")
            .and(takesArgument(0, Object.class))
            .and(returns(Integer.class)),
        this.getClass().getName() + "$VirtualFieldGetAdvice");
    transformer.applyAdviceToMethod(
        named("localValue").and(takesArgument(0, int[].class)).and(returns(int[].class)),
        this.getClass().getName() + "$LocalVariableAdvice");
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

  public static class VirtualFieldSetAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Object target, @Advice.Argument(1) Integer value) {
      VirtualField<Object, Integer> field = VirtualField.find(Object.class, Integer.class);
      field.set(target, value);
    }
  }

  @SuppressWarnings("unused")
  public static class VirtualFieldGetAdvice {
    @SuppressWarnings("UnusedVariable")
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) Object target, @Advice.Return(readOnly = false) Integer returnValue) {
      VirtualField<Object, Integer> field = VirtualField.find(Object.class, Integer.class);
      returnValue = field.get(target);
    }
  }

  @SuppressWarnings("unused")
  public static class LocalVariableAdvice {

    @SuppressWarnings("UnusedVariable")
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) int[] array, @Advice.Local("backup") int[] backupArray) {
      backupArray = Arrays.copyOf(array, array.length);
    }

    @SuppressWarnings("UnusedVariable")
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.Return(readOnly = false) int[] array, @Advice.Local("backup") int[] backupArray) {
      array = Arrays.copyOf(backupArray, backupArray.length);
    }
  }
}
