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
import net.bytebuddy.asm.Advice.AssignReturned;
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

    @AssignReturned.ToReturned
    @Advice.OnMethodExit
    public static String methodExit(@Advice.This Runnable test) {
      VirtualField.find(Runnable.class, String.class).set(test, "instrumented");
      return "instrumented";
    }
  }

  @SuppressWarnings("unused")
  public static class Test2Advice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit
    public static String methodExit(@Advice.This Runnable test) {
      return VirtualField.find(Runnable.class, String.class).get(test);
    }
  }
}
