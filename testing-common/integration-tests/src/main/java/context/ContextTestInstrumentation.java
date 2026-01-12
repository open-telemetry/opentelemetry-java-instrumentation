/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package context;

import static context.ContextTestSingletons.CONTEXT;
import static context.ContextTestSingletons.INTEGER;
import static context.ContextTestSingletons.INTERFACE_INTEGER;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import library.KeyClass;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ContextTestInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return nameStartsWith("library.");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("isInstrumented"), this.getClass().getName() + "$MarkInstrumentedAdvice");
    transformer.applyAdviceToMethod(
        named("incrementContextCount"),
        this.getClass().getName() + "$StoreAndIncrementApiUsageAdvice");
    transformer.applyAdviceToMethod(
        named("getContextCount"), this.getClass().getName() + "$GetApiUsageAdvice");
    transformer.applyAdviceToMethod(
        named("putContextCount"), this.getClass().getName() + "$PutApiUsageAdvice");
    transformer.applyAdviceToMethod(
        named("removeContextCount"), this.getClass().getName() + "$RemoveApiUsageAdvice");
    transformer.applyAdviceToMethod(
        named("useMultipleFields"), this.getClass().getName() + "$UseMultipleFieldsAdvice");
  }

  @SuppressWarnings("unused")
  public static class MarkInstrumentedAdvice {
    @AssignReturned.ToReturned
    @Advice.OnMethodExit
    public static boolean methodExit() {
      return true;
    }
  }

  @SuppressWarnings("unused")
  public static class StoreAndIncrementApiUsageAdvice {
    @AssignReturned.ToReturned
    @Advice.OnMethodExit
    public static int methodExit(@Advice.This KeyClass thiz) {
      Context context = CONTEXT.get(thiz);
      if (context == null) {
        context = new Context();
        CONTEXT.set(thiz, context);
      }

      return ++context.count;
    }
  }

  @SuppressWarnings("unused")
  public static class GetApiUsageAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean methodEnter() {
      // always skip original method body
      return true;
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit
    public static int methodExit(@Advice.This KeyClass thiz) {
      Context context = CONTEXT.get(thiz);
      return context == null ? 0 : context.count;
    }
  }

  @SuppressWarnings("unused")
  public static class PutApiUsageAdvice {
    @Advice.OnMethodExit
    public static void methodExit(@Advice.This KeyClass thiz, @Advice.Argument(0) int value) {
      Context context = new Context();
      context.count = value;
      CONTEXT.set(thiz, context);
    }
  }

  @SuppressWarnings("unused")
  public static class RemoveApiUsageAdvice {
    @Advice.OnMethodExit
    public static void methodExit(@Advice.This KeyClass thiz) {
      CONTEXT.set(thiz, null);
    }
  }

  @SuppressWarnings("unused")
  public static class UseMultipleFieldsAdvice {
    @Advice.OnMethodExit
    public static void methodExit(@Advice.This KeyClass thiz) {
      Context context = CONTEXT.get(thiz);
      int count = context == null ? 0 : context.count;
      INTEGER.set(thiz, count);
      INTERFACE_INTEGER.set(thiz, count);
    }
  }
}
