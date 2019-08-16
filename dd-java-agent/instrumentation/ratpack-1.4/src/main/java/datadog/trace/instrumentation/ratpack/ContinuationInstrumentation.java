package datadog.trace.instrumentation.ratpack;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
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
import ratpack.func.Block;
import ratpack.path.PathBinding;

@AutoService(Instrumenter.class)
public final class ContinuationInstrumentation extends Instrumenter.Default {

  public ContinuationInstrumentation() {
    super("ratpack");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return safeHasSuperType(named("ratpack.exec.internal.Continuation"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      getClass().getName() + "$ResumeAdvice$BlockWrapper",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("resume").and(takesArgument(0, named("ratpack.func.Block"))),
        ResumeAdvice.class.getName());
  }

  public static class ResumeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrap(@Advice.Argument(value = 0, readOnly = false) Block block) {
      final Span span = GlobalTracer.get().activeSpan();
      if (span != null) {
        block = BlockWrapper.wrapIfNeeded(block, span);
      }
    }

    public void muzzleCheck(final PathBinding binding) {
      // This was added in 1.4.  Added here to ensure consistency with other instrumentation.
      binding.getDescription();
    }

    @Slf4j
    public static class BlockWrapper implements Block {
      private final Block delegate;
      private final Span span;

      private BlockWrapper(final Block delegate, final Span span) {
        assert span != null;
        this.delegate = delegate;
        this.span = span;
      }

      @Override
      public void execute() throws Exception {
          try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, false)) {
            if (scope instanceof TraceScope) {
              ((TraceScope) scope).setAsyncPropagation(true);
            }
            delegate.execute();
          }
      }

      public static Block wrapIfNeeded(final Block delegate, final Span span) {
        if (delegate instanceof BlockWrapper) {
          return delegate;
        }
        log.debug("Wrapping block {}", delegate);
        return new BlockWrapper(delegate, span);
      }
    }
  }
}
