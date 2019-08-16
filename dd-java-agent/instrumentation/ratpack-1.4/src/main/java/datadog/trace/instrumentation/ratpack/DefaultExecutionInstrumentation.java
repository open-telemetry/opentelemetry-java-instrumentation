package datadog.trace.instrumentation.ratpack;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
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
      getClass().getName() + "$DelimitAdvice$ActionWrapper",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        nameStartsWith("delimit") // include delimitStream
            .and(takesArgument(0, named("ratpack.func.Action")))
            .and(takesArgument(1, named("ratpack.func.Action"))),
        DelimitAdvice.class.getName());
  }

  public static class DelimitAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrap(
        @Advice.Argument(value = 0, readOnly = false) Action<? super Throwable> onError,
        @Advice.Argument(value = 1, readOnly = false) Action<? super Continuation> segment) {
      final Span span = GlobalTracer.get().activeSpan();
      if (span != null) {
        /**
         * Here we pass along the span instead of a continuation because we aren't sure it won't be
         * used for both callbacks.
         */
        onError = ActionWrapper.wrapIfNeeded(onError, span);
        segment = ActionWrapper.wrapIfNeeded(segment, span);
      }
    }

    public void muzzleCheck(final PathBinding binding) {
      // This was added in 1.4.  Added here to ensure consistency with other instrumentation.
      binding.getDescription();
    }

    @Slf4j
    public static class ActionWrapper<T> implements Action<T> {
      private final Action<T> delegate;
      private final Span span;

      private ActionWrapper(final Action<T> delegate, final Span span) {
        assert span != null;
        this.delegate = delegate;
        this.span = span;
      }

      @Override
      public void execute(final T t) throws Exception {
          try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, false)) {
            if (scope instanceof TraceScope) {
              ((TraceScope) scope).setAsyncPropagation(true);
            }
            delegate.execute(t);
          }
      }

      public static <T> Action<T> wrapIfNeeded(final Action<T> delegate, final Span span) {
        if (delegate instanceof ActionWrapper || span == null) {
          return delegate;
        }
        log.debug("Wrapping action task {}", delegate);
        return new ActionWrapper(delegate, span);
      }
    }
  }
}
