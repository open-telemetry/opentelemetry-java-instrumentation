package datadog.trace.instrumentation.ratpack;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static datadog.trace.agent.tooling.bytebuddy.matcher.DDElementMatchers.implementsInterface;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import com.google.common.net.HostAndPort;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import ratpack.func.Block;
import ratpack.path.PathBinding;

@AutoService(Instrumenter.class)
public final class ContinuationInstrumentation extends Instrumenter.Default {

  public ContinuationInstrumentation() {
    super("ratpack");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("ratpack.exec.internal.Continuation");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return nameStartsWith("ratpack.exec.")
        .<TypeDescription>and(implementsInterface(named("ratpack.exec.internal.Continuation")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".BlockWrapper",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("resume").and(takesArgument(0, named("ratpack.func.Block"))),
        ContinuationInstrumentation.class.getName() + "$ResumeAdvice");
  }

  public static class ResumeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrap(@Advice.Argument(value = 0, readOnly = false) Block block) {
      block = BlockWrapper.wrapIfNeeded(block, activeSpan());
    }

    public void muzzleCheck(final PathBinding binding, final HostAndPort host) {
      // This was added in 1.4.  Added here to ensure consistency with other instrumentation.
      binding.getDescription();

      // This is available in Guava 20 which was required starting in 1.5
      host.getHost();
    }
  }
}
