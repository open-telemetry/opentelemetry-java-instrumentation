package io.opentelemetry.auto.instrumentation.lettuce.v5_2;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.lettuce.core.resource.DefaultClientResources;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class LettuceClientResourcesInstrumentation extends Instrumenter.Default {

  public LettuceClientResourcesInstrumentation() {
    super("lettuce", "lettuce-5", "lettuce-5.2");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("io.lettuce.core.resource.DefaultClientResources");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
        packageName + ".LettuceClientDecorator",
        packageName + ".OpenTelemetryTracing"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(isPublic()).and(isStatic()).and(named("builder")),
        LettuceClientResourcesInstrumentation.class.getName() + "$DefaultClientResourcesAdvice");
  }

  public static class DefaultClientResourcesAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodEnter(@Advice.Return final DefaultClientResources.Builder builder) {
      builder.tracing(OpenTelemetryTracing.INSTANCE);
    }
  }
}
