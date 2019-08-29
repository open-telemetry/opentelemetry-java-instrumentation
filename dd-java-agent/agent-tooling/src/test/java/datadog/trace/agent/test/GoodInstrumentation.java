package datadog.trace.agent.test;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class GoodInstrumentation extends Instrumenter.Default {

  public GoodInstrumentation() {
    super("good-test-instrumentation");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named(TestClass.class.getName());
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(named("doSomething"), GoodInstrumentationAdvice.class.getName());
  }

  public static class GoodInstrumentationAdvice {
    @Advice.OnMethodExit
    public static void changeReturn(@Advice.Return(readOnly = false) String originalValue) {
      originalValue = "overridden value";
    }
  }
}
