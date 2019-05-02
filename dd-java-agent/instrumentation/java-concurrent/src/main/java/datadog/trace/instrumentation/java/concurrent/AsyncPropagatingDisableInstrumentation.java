package datadog.trace.instrumentation.java.concurrent;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableMap;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.noop.NoopSpan;
import io.opentracing.util.GlobalTracer;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * Sometimes classes do lazy initialization for scheduling of tasks. If this is done during a trace
 * it can cause the trace to never be reported. Add matchers below to disable async propagation
 * during this period.
 */
@Slf4j
@AutoService(Instrumenter.class)
public final class AsyncPropagatingDisableInstrumentation implements Instrumenter {

  private static final Map<
          ElementMatcher<? super TypeDescription>, ElementMatcher<? super MethodDescription>>
      CLASS_AND_METHODS =
          new ImmutableMap.Builder<
                  ElementMatcher<? super TypeDescription>,
                  ElementMatcher<? super MethodDescription>>()
              .put(safeHasSuperType(named("rx.Scheduler$Worker")), named("schedulePeriodically"))
              .build();

  @Override
  public AgentBuilder instrument(AgentBuilder agentBuilder) {

    for (final Map.Entry<
            ElementMatcher<? super TypeDescription>, ElementMatcher<? super MethodDescription>>
        entry : CLASS_AND_METHODS.entrySet()) {
      agentBuilder =
          new DisableAsyncInstrumentation(entry.getKey(), entry.getValue())
              .instrument(agentBuilder);
    }
    return agentBuilder;
  }

  // Not Using AutoService to hook up this instrumentation
  public static class DisableAsyncInstrumentation extends Default {

    private final ElementMatcher<? super TypeDescription> typeMatcher;
    private final ElementMatcher<? super MethodDescription> methodMatcher;

    /** No-arg constructor only used by muzzle and tests. */
    public DisableAsyncInstrumentation() {
      this(ElementMatchers.<TypeDescription>none(), ElementMatchers.<MethodDescription>none());
    }

    public DisableAsyncInstrumentation(
        final ElementMatcher<? super TypeDescription> typeMatcher,
        final ElementMatcher<? super MethodDescription> methodMatcher) {
      super(AbstractExecutorInstrumentation.EXEC_NAME);
      this.typeMatcher = typeMatcher;
      this.methodMatcher = methodMatcher;
    }

    @Override
    public ElementMatcher<? super TypeDescription> typeMatcher() {
      return typeMatcher;
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(methodMatcher, DisableAsyncAdvice.class.getName());
    }
  }

  public static class DisableAsyncAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope enter() {
      final ScopeManager scopeManager = GlobalTracer.get().scopeManager();
      final Scope scope = scopeManager.active();
      if (scope instanceof TraceScope && ((TraceScope) scope).isAsyncPropagating()) {
        return scopeManager.activate(NoopSpan.INSTANCE, false);
      }
      return null;
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void exit(@Advice.Enter final Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
