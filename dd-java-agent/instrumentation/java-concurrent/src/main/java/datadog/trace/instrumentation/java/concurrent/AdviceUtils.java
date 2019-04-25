package datadog.trace.instrumentation.java.concurrent;

import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.instrumentation.java.concurrent.State;
import datadog.trace.context.TraceScope;
import lombok.extern.slf4j.Slf4j;

/** Helper utils for Runnable/Callable instrumentation */
@Slf4j
public class AdviceUtils {

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
