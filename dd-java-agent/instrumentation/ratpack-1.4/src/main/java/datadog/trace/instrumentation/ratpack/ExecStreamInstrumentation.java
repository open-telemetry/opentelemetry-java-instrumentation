package datadog.trace.instrumentation.ratpack;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import ratpack.exec.internal.Continuation;
import ratpack.func.Action;
import ratpack.path.PathBinding;

@AutoService(Instrumenter.class)
public final class ExecStreamInstrumentation extends Instrumenter.Default {

  public ExecStreamInstrumentation() {
    super("ratpack");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(safeHasSuperType(named("ratpack.exec.internal.DefaultExecution")));
  }

  // ratpack.exec.internal.DefaultExecution.delimit
  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".ExecStreamInstrumentation$ActionWrapper",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return Collections.singletonMap(
        named("delimit")
            .or(named("delimitStream"))
            .and(takesArgument(0, named("ratpack.func.Action")))
            .and(takesArgument(1, named("ratpack.func.Action"))),
        WrapActionAdvice.class.getName());
  }

  public static class WrapActionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrap(
        @Advice.Argument(value = 0, readOnly = false) Action<Throwable> onError,
        @Advice.Argument(value = 1, readOnly = false) Action<Continuation> segment) {
      final Scope scope = GlobalTracer.get().scopeManager().active();
      if (scope instanceof TraceScope) {
        final TraceScope.Continuation continuation = ((TraceScope) scope).capture();
        onError = ActionWrapper.wrapIfNeeded(onError, continuation);
        segment = ActionWrapper.wrapIfNeeded(segment, continuation);
      }
    }

    public void muzzleCheck(final PathBinding binding) {
      // This was added in 1.4.  Added here to ensure consistency with other instrumentation.
      binding.getDescription();
    }
  }

  @Slf4j
  public static class ActionWrapper<T> implements Action<T> {
    private final Action<T> delegate;
    private final TraceScope.Continuation traceContinuation;

    private ActionWrapper(
        final Action<T> delegate, final TraceScope.Continuation traceContinuation) {
      this.delegate = delegate;
      this.traceContinuation = traceContinuation;
    }

    @Override
    public void execute(final T subject) throws Exception {
      if (traceContinuation != null) {
        try (final TraceScope scope = traceContinuation.activate()) {
          scope.setAsyncPropagation(true);
          delegate.execute(subject);
        }
      } else {
        delegate.execute(subject);
      }
    }

    public static <T> Action<T> wrapIfNeeded(
        final Action<T> delegate, final TraceScope.Continuation traceContinuation) {
      if (delegate instanceof ActionWrapper) {
        return delegate;
      }
      log.debug("Wrapping action task {}", delegate);
      return new ActionWrapper<>(delegate, traceContinuation);
    }
  }
}
