package datadog.trace.instrumentation.ratpack;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import com.google.common.net.HostAndPort;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import ratpack.exec.internal.Continuation;
import ratpack.func.Action;
import ratpack.path.PathBinding;

@AutoService(Instrumenter.class)
public final class DefaultExecutionInstrumentation extends Instrumenter.Default {

  public DefaultExecutionInstrumentation() {
    super("ratpack");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("ratpack.exec.internal.DefaultExecution");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".ActionWrapper",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        nameStartsWith("delimit") // include delimitStream
            .and(takesArgument(0, named("ratpack.func.Action")))
            .and(takesArgument(1, named("ratpack.func.Action"))),
        DefaultExecutionInstrumentation.class.getName() + "$DelimitAdvice");
  }

  public static class DelimitAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrap(
        @Advice.Argument(value = 0, readOnly = false) Action<? super Throwable> onError,
        @Advice.Argument(value = 1, readOnly = false) Action<? super Continuation> segment) {
      /**
       * Here we pass along the span instead of a continuation because we aren't sure the callback
       * will actually be called.
       */
      final AgentSpan span = activeSpan();
      onError = ActionWrapper.wrapIfNeeded(onError, span);
      segment = ActionWrapper.wrapIfNeeded(segment, span);
    }

    public void muzzleCheck(final PathBinding binding, final HostAndPort host) {
      // This was added in 1.4.  Added here to ensure consistency with other instrumentation.
      binding.getDescription();

      // This is available in Guava 20 which was required starting in 1.5
      host.getHost();
    }
  }
}
