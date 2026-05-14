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
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class SmokeInlinedInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.opentelemetry.smoketest.extensions.app.AppMain");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("returnValue").and(takesArgument(0, int.class)),
        getClass().getName() + "$ModifyReturnValueAdvice");
    transformer.applyAdviceToMethod(
        named("methodArguments").and(takesArgument(0, int.class)),
        getClass().getName() + "$ModifyArgumentsAdvice");
    transformer.applyAdviceToMethod(
        named("setVirtualFieldValue")
            .and(takesArgument(0, Object.class))
            .and(takesArgument(1, Integer.class)),
        getClass().getName() + "$VirtualFieldSetAdvice");
    transformer.applyAdviceToMethod(
        named("getVirtualFieldValue")
            .and(takesArgument(0, Object.class))
            .and(returns(Integer.class)),
        getClass().getName() + "$VirtualFieldGetAdvice");
    transformer.applyAdviceToMethod(
        named("localValue").and(takesArgument(0, int[].class)).and(returns(int[].class)),
        getClass().getName() + "$LocalVariableAdvice");
  }

  @SuppressWarnings("unused")
  public static class ModifyReturnValueAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    @AssignReturned.ToReturned
    public static int onExit(@Advice.Return int returnValue) {
      return returnValue + 1;
    }
  }

  @SuppressWarnings("unused")
  public static class ModifyArgumentsAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    @AssignReturned.ToArguments(@ToArgument(0))
    public static int onEnter(@Advice.Argument(0) int argument) {
      return argument - 1;
    }
  }

  @SuppressWarnings("unused")
  public static class VirtualFieldSetAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Object target, @Advice.Argument(1) Integer value) {
      VirtualFieldHelper.field.set(target, value);
    }
  }

  @SuppressWarnings("unused")
  public static class VirtualFieldGetAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    @AssignReturned.ToReturned
    public static Integer onExit(@Advice.Argument(0) Object target) {
      return VirtualFieldHelper.field.get(target);
    }
  }

  @SuppressWarnings("unused")
  public static class LocalVariableAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static int[] onEnter(@Advice.Argument(0) int[] array) {
      return Arrays.copyOf(array, array.length);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    @AssignReturned.ToReturned(index = 0)
    public static int[][] onExit(@Advice.Enter int[] backupArray) {
      return new int[][] {Arrays.copyOf(backupArray, backupArray.length)};
    }
  }

  public static class VirtualFieldHelper {
    public static final VirtualField<Object, Integer> field =
        VirtualField.find(Object.class, Integer.class);

    private VirtualFieldHelper() {}
  }
}
