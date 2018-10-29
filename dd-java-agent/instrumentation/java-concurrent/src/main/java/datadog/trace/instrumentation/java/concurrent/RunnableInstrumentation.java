package datadog.trace.instrumentation.java.concurrent;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.instrumentation.java.concurrent.State;
import datadog.trace.context.TraceScope;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@Slf4j
@AutoService(Instrumenter.class)
public final class RunnableInstrumentation extends Instrumenter.Default {

  public RunnableInstrumentation() {
    super(ExecutorInstrumentation.EXEC_NAME);
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(safeHasSuperType(named(Runnable.class.getName()).or(named(Callable.class.getName()))));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      RunnableInstrumentation.class.getName() + "$RunnableUtils",
    };
  }

  @Override
  public Map<String, String> contextStore() {
    final Map<String, String> map = new HashMap<>();
    map.put("java.lang.Runnable", State.class.getName());
    map.put("java.util.concurrent.Callable", State.class.getName());
    return Collections.unmodifiableMap(map);
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    final Map<ElementMatcher, String> transformers = new HashMap<>();
    transformers.put(named("run").and(takesArguments(0)), RunnableAdvice.class.getName());
    transformers.put(named("call").and(takesArguments(0)), CallableAdvice.class.getName());
    return transformers;
  }

  public static class RunnableAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static TraceScope enter(@Advice.This final Runnable thiz) {
      final ContextStore<Runnable, State> contextStore =
          InstrumentationContext.get(Runnable.class, State.class);
      return RunnableUtils.startTaskScope(contextStore, thiz);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter final TraceScope scope) {
      RunnableUtils.endTaskScope(scope);
    }
  }

  public static class CallableAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static TraceScope enter(@Advice.This final Callable thiz) {
      final ContextStore<Callable, State> contextStore =
          InstrumentationContext.get(Callable.class, State.class);
      return RunnableUtils.startTaskScope(contextStore, thiz);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter final TraceScope scope) {
      RunnableUtils.endTaskScope(scope);
    }
  }

  /** Helper utils for Runnable/Callable instrumentation */
  @Slf4j
  public static class RunnableUtils {

    /**
     * Start scope for a given task
     *
     * @param contextStore context storage for task's state
     * @param task task to start scope for
     * @param <T> task's type
     * @return scope if scope was started, or null
     */
    public static <T> TraceScope startTaskScope(
        final ContextStore<T, State> contextStore, final T task) {
      final State state = contextStore.get(task);
      if (state != null) {
        final TraceScope.Continuation continuation = state.getAndResetContinuation();
        if (continuation != null) {
          final TraceScope scope = continuation.activate();
          scope.setAsyncPropagation(true);
          return scope;
        }
      }
      return null;
    }

    public static void endTaskScope(final TraceScope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
