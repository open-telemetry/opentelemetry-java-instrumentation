package datadog.trace.instrumentation.java.concurrent;

import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.WeakMap;
import datadog.trace.bootstrap.instrumentation.java.concurrent.CallableWrapper;
import datadog.trace.bootstrap.instrumentation.java.concurrent.RunnableWrapper;
import datadog.trace.bootstrap.instrumentation.java.concurrent.State;
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.util.GlobalTracer;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;

/** Utils for concurrent instrumentations. */
@Slf4j
public class ExecutorInstrumentationUtils {

  private static final WeakMap<Executor, Boolean> EXECUTORS_DISABLED_FOR_WRAPPED_TASKS =
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
        && !ExecutorInstrumentationUtils.isExecutorDisabledForThisTask(executor, task));
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

  public static void disableExecutorForWrappedTasks(final Executor executor) {
    log.debug("Disabling Executor tracing for wrapped tasks for instance {}", executor);
    EXECUTORS_DISABLED_FOR_WRAPPED_TASKS.put(executor, true);
  }

  /**
   * Check if Executor can accept given task.
   *
   * <p>Disabled executors cannot accept wrapped tasks, non wrapped tasks (i.e. tasks with injected
   * fields) should still work fine.
   */
  public static boolean isExecutorDisabledForThisTask(final Executor executor, final Object task) {
    return (task instanceof RunnableWrapper || task instanceof CallableWrapper)
        && EXECUTORS_DISABLED_FOR_WRAPPED_TASKS.containsKey(executor);
  }
}
