/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class TestTypeInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("TestClass");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("testMethod"), TestTypeInstrumentation.class.getName() + "$TestAdvice");
    transformer.applyAdviceToMethod(
        named("testMethod2"), TestTypeInstrumentation.class.getName() + "$Test2Advice");
  }

  @SuppressWarnings("unused")
  public static class TestAdvice {

    @Advice.OnMethodExit
    public static void methodExit(
        @Advice.This Runnable test, @Advice.Return(readOnly = false) String result) {
      VirtualField.find(Runnable.class, String.class).set(test, "instrumented");
      result = "instrumented";
    }
  }

  @SuppressWarnings("unused")
  public static class Test2Advice {

    @Advice.OnMethodExit
    public static void methodExit(
        @Advice.This Runnable test, @Advice.Return(readOnly = false) String result) {
      result = VirtualField.find(Runnable.class, String.class).get(test);
    }
  }
}
