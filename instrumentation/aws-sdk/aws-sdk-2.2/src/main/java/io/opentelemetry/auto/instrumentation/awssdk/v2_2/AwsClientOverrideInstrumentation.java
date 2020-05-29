package io.opentelemetry.auto.instrumentation.awssdk.v2_2;

import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;

/**
 * Separate instrumentation to inject into user configuration overrides. Overrides aren't merged so
 * we need to either inject into their override or create our own, but not both.
 */
@AutoService(Instrumenter.class)
public final class AwsClientOverrideInstrumentation extends AbstractAwsClientInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("software.amazon.awssdk.core.client.config.ClientOverrideConfiguration");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("software.amazon.awssdk.core.client.config.ClientOverrideConfiguration");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return Collections.singletonMap(
        isMethod().and(isPublic()).and(isStatic()).and(named("builder")),
        AwsClientOverrideInstrumentation.class.getName() + "$AwsSdkClientOverrideAdvice");
  }

  public static class AwsSdkClientOverrideAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Return final ClientOverrideConfiguration.Builder builder) {
      TracingExecutionInterceptor.overrideConfiguration(builder);
    }
  }
}
