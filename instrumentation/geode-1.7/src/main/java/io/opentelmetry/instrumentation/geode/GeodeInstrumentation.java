package io.opentelmetry.instrumentation.geode;

import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class GeodeInstrumentation extends Instrumenter.Default {
  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return instanceO
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return null;
  }
}
