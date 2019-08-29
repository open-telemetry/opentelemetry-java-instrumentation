package datadog.trace.agent.test;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class BadInstrumentation extends Instrumenter.Default {
  public BadInstrumentation() {
    super("bad-test-instrumentation");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    throw new RuntimeException("Test Exception");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return Collections.emptyMap();
  }
}
