/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package context;

import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import library.KeyClass;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
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
  protected String[] additionalHelperClassNames() {
    return new String[] {getClass().getName() + "$Context"};
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ContextTestInstrumentation());
  }

  @Override
  public Map<String, String> contextStore() {
    Map<String, String> store = new HashMap<>(3);
    store.put("library.KeyClass", getClass().getName() + "$Context");
    store.put("library.UntransformableKeyClass", getClass().getName() + "$Context");
    store.put("library.DisabledKeyClass", getClass().getName() + "$Context");
    return store;
  }

  public static class ContextTestInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<? super TypeDescription> typeMatcher() {
      return nameStartsWith("library.");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>(7);
      transformers.put(named("isInstrumented"), MarkInstrumentedAdvice.class.getName());
      transformers.put(
          named("incrementContextCount"), StoreAndIncrementApiUsageAdvice.class.getName());
      transformers.put(named("getContextCount"), GetApiUsageAdvice.class.getName());
      transformers.put(named("putContextCount"), PutApiUsageAdvice.class.getName());
      transformers.put(named("removeContextCount"), RemoveApiUsageAdvice.class.getName());
      transformers.put(
          named("incorrectKeyClassUsage"), IncorrectKeyClassContextApiUsageAdvice.class.getName());
      transformers.put(
          named("incorrectContextClassUsage"),
          IncorrectContextClassContextApiUsageAdvice.class.getName());
      transformers.put(
          named("incorrectCallUsage"), IncorrectCallContextApiUsageAdvice.class.getName());
      return transformers;
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

  public static class IncorrectKeyClassContextApiUsageAdvice {
    @Advice.OnMethodExit
    public static void methodExit() {
      InstrumentationContext.get(Object.class, Context.class);
    }
  }

  public static class IncorrectContextClassContextApiUsageAdvice {
    @Advice.OnMethodExit
    public static void methodExit() {
      InstrumentationContext.get(KeyClass.class, Object.class);
    }
  }

  public static class IncorrectCallContextApiUsageAdvice {
    @Advice.OnMethodExit
    public static void methodExit() {
      // Our instrumentation doesn't handle variables being passed to InstrumentationContext.get,
      // so we make sure that this actually fails instrumentation.
      Class clazz = null;
      InstrumentationContext.get(clazz, Object.class);
    }
  }

  public static class Context {
    public static final ContextStore.Factory<Context> FACTORY = Context::new;

    public int count = 0;
  }
}
