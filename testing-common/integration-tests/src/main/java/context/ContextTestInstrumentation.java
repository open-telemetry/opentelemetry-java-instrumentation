/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package context;

import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import library.KeyClass;
import library.KeyInterface;
import net.bytebuddy.asm.Advice;
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
    @Advice.OnMethodExit
    public static void methodExit(@Advice.Return(readOnly = false) boolean isInstrumented) {
      isInstrumented = true;
    }
  }

  @SuppressWarnings("unused")
  public static class StoreAndIncrementApiUsageAdvice {
    @Advice.OnMethodExit
    public static void methodExit(
        @Advice.This KeyClass thiz, @Advice.Return(readOnly = false) int contextCount) {

      VirtualField<KeyClass, Context> virtualField =
          VirtualField.find(KeyClass.class, Context.class);

      Context context = virtualField.get(thiz);
      if (context == null) {
        context = new Context();
        virtualField.set(thiz, context);
      }

      contextCount = ++context.count;
    }
  }

  @SuppressWarnings("unused")
  public static class GetApiUsageAdvice {
    @Advice.OnMethodExit
    public static void methodExit(
        @Advice.This KeyClass thiz, @Advice.Return(readOnly = false) int contextCount) {
      VirtualField<KeyClass, Context> virtualField =
          VirtualField.find(KeyClass.class, Context.class);
      Context context = virtualField.get(thiz);
      contextCount = context == null ? 0 : context.count;
    }
  }

  @SuppressWarnings("unused")
  public static class PutApiUsageAdvice {
    @Advice.OnMethodExit
    public static void methodExit(@Advice.This KeyClass thiz, @Advice.Argument(0) int value) {
      VirtualField<KeyClass, Context> virtualField =
          VirtualField.find(KeyClass.class, Context.class);
      Context context = new Context();
      context.count = value;
      virtualField.set(thiz, context);
    }
  }

  @SuppressWarnings("unused")
  public static class RemoveApiUsageAdvice {
    @Advice.OnMethodExit
    public static void methodExit(@Advice.This KeyClass thiz) {
      VirtualField<KeyClass, Context> virtualField =
          VirtualField.find(KeyClass.class, Context.class);
      virtualField.set(thiz, null);
    }
  }

  @SuppressWarnings("unused")
  public static class UseMultipleFieldsAdvice {
    @Advice.OnMethodExit
    public static void methodExit(@Advice.This KeyClass thiz) {
      VirtualField<KeyClass, Context> field1 = VirtualField.find(KeyClass.class, Context.class);
      VirtualField<KeyClass, Integer> field2 = VirtualField.find(KeyClass.class, Integer.class);
      VirtualField<KeyInterface, Integer> interfaceField =
          VirtualField.find(KeyInterface.class, Integer.class);

      Context context = field1.get(thiz);
      int count = context == null ? 0 : context.count;
      field2.set(thiz, count);
      interfaceField.set(thiz, count);
    }
  }
}
