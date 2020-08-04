/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package context;

import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.instrumentation.auto.api.ContextStore;
import io.opentelemetry.instrumentation.auto.api.InstrumentationContext;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class ContextTestInstrumentation extends Instrumenter.Default {
  public ContextTestInstrumentation() {
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
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return nameStartsWith(getClass().getName() + "$");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>(7);
    transformers.put(named("isInstrumented"), MarkInstrumentedAdvice.class.getName());
    transformers.put(
        named("incrementContextCount"), StoreAndIncrementApiUsageAdvice.class.getName());
    transformers.put(named("getContextCount"), GetApiUsageAdvice.class.getName());
    transformers.put(named("putContextCount"), PutApiUsageAdvice.class.getName());
    transformers.put(
        named("incorrectKeyClassUsage"), IncorrectKeyClassContextApiUsageAdvice.class.getName());
    transformers.put(
        named("incorrectContextClassUsage"),
        IncorrectContextClassContextApiUsageAdvice.class.getName());
    transformers.put(
        named("incorrectCallUsage"), IncorrectCallContextApiUsageAdvice.class.getName());
    return transformers;
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {getClass().getName() + "$Context", getClass().getName() + "$Context$1"};
  }

  @Override
  public Map<String, String> contextStore() {
    Map<String, String> store = new HashMap<>(2);
    store.put(getClass().getName() + "$KeyClass", getClass().getName() + "$Context");
    store.put(getClass().getName() + "$UntransformableKeyClass", getClass().getName() + "$Context");
    store.put(getClass().getName() + "$DisabledKeyClass", getClass().getName() + "$Context");
    return store;
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
        @Advice.This final KeyClass thiz, @Advice.Return(readOnly = false) int contextCount) {
      ContextStore<KeyClass, Context> contextStore =
          InstrumentationContext.get(KeyClass.class, Context.class);
      Context context = contextStore.putIfAbsent(thiz, new Context());
      contextCount = ++context.count;
    }
  }

  public static class StoreAndIncrementWithFactoryApiUsageAdvice {
    @Advice.OnMethodExit
    public static void methodExit(
        @Advice.This final KeyClass thiz, @Advice.Return(readOnly = false) int contextCount) {
      ContextStore<KeyClass, Context> contextStore =
          InstrumentationContext.get(KeyClass.class, Context.class);
      Context context = contextStore.putIfAbsent(thiz, Context.FACTORY);
      contextCount = ++context.count;
    }
  }

  public static class GetApiUsageAdvice {
    @Advice.OnMethodExit
    public static void methodExit(
        @Advice.This final KeyClass thiz, @Advice.Return(readOnly = false) int contextCount) {
      ContextStore<KeyClass, Context> contextStore =
          InstrumentationContext.get(KeyClass.class, Context.class);
      contextCount = contextStore.get(thiz).count;
    }
  }

  public static class PutApiUsageAdvice {
    @Advice.OnMethodExit
    public static void methodExit(
        @Advice.This final KeyClass thiz, @Advice.Argument(0) final int value) {
      ContextStore<KeyClass, Context> contextStore =
          InstrumentationContext.get(KeyClass.class, Context.class);
      Context context = new Context();
      context.count = value;
      contextStore.put(thiz, context);
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
    public static final ContextStore.Factory<Context> FACTORY =
        new ContextStore.Factory<Context>() {
          @Override
          public Context create() {
            return new Context();
          }
        };

    int count = 0;
  }

  public static class KeyClass {
    public boolean isInstrumented() {
      // implementation replaced with test instrumentation
      return false;
    }

    public int incrementContextCount() {
      // implementation replaced with test instrumentation
      return -1;
    }

    public int incrementContextCountWithFactory() {
      // implementation replaced with test instrumentation
      return -1;
    }

    public int getContextCount() {
      // implementation replaced with test instrumentation
      return -1;
    }

    public void putContextCount(final int value) {
      // implementation replaced with test instrumentation
    }
  }

  /**
   * A class which will not be transformed by our instrumentation due to {@link
   * FieldBackedProviderTest#shouldTransformClass(String, ClassLoader)}.
   */
  public static class UntransformableKeyClass extends KeyClass {
    @Override
    public boolean isInstrumented() {
      return false;
    }
  }

  /** A class that is used that field injection can be disabled. */
  public static class DisabledKeyClass extends KeyClass {
    @Override
    public boolean isInstrumented() {
      return false;
    }
  }

  public static class IncorrectKeyClassUsageKeyClass {
    public boolean isInstrumented() {
      return false;
    }

    public int incorrectKeyClassUsage() {
      // instrumentation will not apply to this class because advice incorrectly uses context api
      return -1;
    }
  }

  public static class IncorrectContextClassUsageKeyClass {
    public boolean isInstrumented() {
      return false;
    }

    public int incorrectContextClassUsage() {
      // instrumentation will not apply to this class because advice incorrectly uses context api
      return -1;
    }
  }

  public static class IncorrectCallUsageKeyClass {
    public boolean isInstrumented() {
      return false;
    }

    public int incorrectCallUsage() {
      // instrumentation will not apply to this class because advice incorrectly uses context api
      return -1;
    }
  }
}
