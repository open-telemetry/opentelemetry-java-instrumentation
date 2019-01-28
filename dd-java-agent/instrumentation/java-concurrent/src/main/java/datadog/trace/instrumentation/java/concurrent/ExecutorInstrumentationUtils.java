package datadog.trace.instrumentation.java.concurrent;

import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.WeakMap;
import datadog.trace.bootstrap.instrumentation.java.concurrent.State;
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.util.GlobalTracer;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;

/** Utils for concurrent instrumentations. */
@Slf4j
public class ExecutorInstrumentationUtils {

  private static final WeakMap<Executor, Boolean> DISABLED_EXECUTORS =
      WeakMap.Provider.newWeakMap();

  /**
   * Checks if given task should get state attached.
   *
   * @param task task object
   * @param executor executor this task was scheduled on
   * @return true iff given task object should be wrapped
   */
  public static boolean shouldAttachStateToTask(final Object task, final Executor executor) {
    final Scope scope = GlobalTracer.get().scopeManager().active();
    return (scope instanceof TraceScope
        && ((TraceScope) scope).isAsyncPropagating()
        && task != null
        && !ExecutorInstrumentationUtils.isDisabled(executor));
  }

  /**
   * Create task state given current scope.
   *
   * @param contextStore context storage
   * @param task task instance
   * @param scope current scope
   * @param <T> task class type
   * @return new state
   */
  public static <T> State setupState(
      final ContextStore<T, State> contextStore, final T task, final TraceScope scope) {
    final State state = contextStore.putIfAbsent(task, State.FACTORY);
    final TraceScope.Continuation continuation = scope.capture();
    if (state.setContinuation(continuation)) {
      log.debug("created continuation {} from scope {}, state: {}", continuation, scope, state);
    } else {
      continuation.close(false);
    }
    return state;
  }

  /**
   * Clean up after job submission method has exited.
   *
   * @param executor the current executor
   * @param state task instrumentation state
   * @param throwable throwable that may have been thrown
   */
  public static void cleanUpOnMethodExit(
      final Executor executor, final State state, final Throwable throwable) {
    if (null != state && null != throwable) {
      /*
      Note: this may potentially close somebody else's continuation if we didn't set it
      up in setupState because it was already present before us. This should be safe but
      may lead to non-attributed async work in some very rare cases.
      Alternative is to not close continuation here if we did not set it up in setupState
      but this may potentially lead to memory leaks if callers do not properly handle
      exceptions.
       */
      state.closeContinuation();
    }
  }

  public static void disableExecutor(final Executor executor) {
    log.debug("Disabling Executor tracing for instance {}", executor);
    DISABLED_EXECUTORS.put(executor, true);
  }

  public static boolean isDisabled(final Executor executor) {
    return DISABLED_EXECUTORS.containsKey(executor);
  }
}
