package datadog.trace.instrumentation.lettuce;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class LettuceClientInstrumentation extends Instrumenter.Default {

  public LettuceClientInstrumentation() {
    super("lettuce");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.lambdaworks.redis.RedisClient");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".LettuceClientDecorator",
      packageName + ".LettuceInstrumentationUtil",
      packageName + ".InstrumentationPoints"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(named("connectStandalone")),
        // Cannot reference class directly here because it would lead to class load failure on Java7
        packageName + ".RedisConnectionAdvice");
  }
}
