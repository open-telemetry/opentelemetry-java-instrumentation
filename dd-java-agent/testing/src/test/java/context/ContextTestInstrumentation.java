package context;

import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.InstrumentationContext;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class ContextTestInstrumentation extends Instrumenter.Default {
  public ContextTestInstrumentation() {
    super("context-test-isntrumenter1");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named(getClass().getPackage().getName() + ".UserClass1")
        .or(named(getClass().getPackage().getName() + ".UserClass2"));
  }

  @Override
  public Map<? extends ElementMatcher, String> transformers() {
    final Map<ElementMatcher, String> transformers = new HashMap<>(2);
    transformers.put(named("isInstrumented"), MarkInstrumentedAdvice.class.getName());
    transformers.put(named("incrementContextCount"), CorrectContextApiUsageAdvice.class.getName());
    transformers.put(
        named("incrementContextCountCountBroken"), IncorrectContextApiUsageAdvice.class.getName());
    return transformers;
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {getClass().getName() + "$UserClass1State"};
  }

  @Override
  public Map<String, String> contextStore() {
    return Collections.singletonMap(
        getClass().getPackage().getName() + ".UserClass1",
        getClass().getName() + "$UserClass1State");
  }

  public static class MarkInstrumentedAdvice {
    @Advice.OnMethodExit
    public static void markInstrumented(@Advice.Return(readOnly = false) boolean isInstrumented) {
      isInstrumented = true;
    }
  }

  public static class CorrectContextApiUsageAdvice {
    @Advice.OnMethodExit
    public static void storeAndIncrement(
        @Advice.This Object thiz, @Advice.Return(readOnly = false) int contextCount) {
      UserClass1State state =
          InstrumentationContext.get((UserClass1) thiz, UserClass1.class, UserClass1State.class);
      contextCount = ++state.count;
    }
  }

  public static class IncorrectContextApiUsageAdvice {
    @Advice.OnMethodExit
    public static void storeAndIncrement(
        @Advice.This Object thiz, @Advice.Return(readOnly = false) int contextCount) {
      UserClass1State state = InstrumentationContext.get(thiz, Object.class, UserClass1State.class);
      contextCount = ++state.count;
    }
  }

  public static class UserClass1State {
    int count = 0;
  }
}
