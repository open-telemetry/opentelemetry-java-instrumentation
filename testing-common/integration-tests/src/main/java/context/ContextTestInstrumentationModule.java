/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package context;

import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import java.util.List;
import library.KeyClass;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class ContextTestInstrumentationModule extends InstrumentationModule {
  public ContextTestInstrumentationModule() {
    super("context-test-instrumentation");
  }

  @Override
  protected boolean defaultEnabled() {
    // this instrumentation is disabled by default, so that it doesn't cause sporadic failures
    // in other tests that do override AgentTestRunner.onInstrumentationError() to filter out
    // the instrumentation errors that this instrumentation purposefully introduces
    return false;
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.equals(getClass().getName() + "$Context");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ContextTestInstrumentation());
  }

  public static class ContextTestInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return nameStartsWith("library.");
    }

    @Override
    public void transform(TypeTransformer transformer) {
      transformer.applyAdviceToMethod(
          named("isInstrumented"), MarkInstrumentedAdvice.class.getName());
      transformer.applyAdviceToMethod(
          named("incrementContextCount"), StoreAndIncrementApiUsageAdvice.class.getName());
      transformer.applyAdviceToMethod(named("getContextCount"), GetApiUsageAdvice.class.getName());
      transformer.applyAdviceToMethod(named("putContextCount"), PutApiUsageAdvice.class.getName());
      transformer.applyAdviceToMethod(
          named("removeContextCount"), RemoveApiUsageAdvice.class.getName());
    }
  }

  public static class MarkInstrumentedAdvice {
    @Advice.OnMethodExit
    public static void methodExit(@Advice.Return(readOnly = false) boolean isInstrumented) {
      isInstrumented = true;
    }
  }

  public static class StoreAndIncrementApiUsageAdvice {
    @Advice.OnMethodExit
    public static void methodExit(
        @Advice.This KeyClass thiz, @Advice.Return(readOnly = false) int contextCount) {
      ContextStore<KeyClass, Context> contextStore =
          InstrumentationContext.get(KeyClass.class, Context.class);
      Context context = contextStore.putIfAbsent(thiz, new Context());
      contextCount = ++context.count;
    }
  }

  public static class StoreAndIncrementWithFactoryApiUsageAdvice {
    @Advice.OnMethodExit
    public static void methodExit(
        @Advice.This KeyClass thiz, @Advice.Return(readOnly = false) int contextCount) {
      ContextStore<KeyClass, Context> contextStore =
          InstrumentationContext.get(KeyClass.class, Context.class);
      Context context = contextStore.putIfAbsent(thiz, Context.FACTORY);
      contextCount = ++context.count;
    }
  }

  public static class GetApiUsageAdvice {
    @Advice.OnMethodExit
    public static void methodExit(
        @Advice.This KeyClass thiz, @Advice.Return(readOnly = false) int contextCount) {
      ContextStore<KeyClass, Context> contextStore =
          InstrumentationContext.get(KeyClass.class, Context.class);
      Context context = contextStore.get(thiz);
      contextCount = context == null ? 0 : context.count;
    }
  }

  public static class PutApiUsageAdvice {
    @Advice.OnMethodExit
    public static void methodExit(@Advice.This KeyClass thiz, @Advice.Argument(0) int value) {
      ContextStore<KeyClass, Context> contextStore =
          InstrumentationContext.get(KeyClass.class, Context.class);
      Context context = new Context();
      context.count = value;
      contextStore.put(thiz, context);
    }
  }

  public static class RemoveApiUsageAdvice {
    @Advice.OnMethodExit
    public static void methodExit(@Advice.This KeyClass thiz) {
      ContextStore<KeyClass, Context> contextStore =
          InstrumentationContext.get(KeyClass.class, Context.class);
      contextStore.put(thiz, null);
    }
  }

  public static class Context {
    public static final ContextStore.Factory<Context> FACTORY = Context::new;

    public int count = 0;
  }
}
