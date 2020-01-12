package io.opentelemetry.auto.instrumentation.java.concurrent;

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activeSpan;

import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.bootstrap.WeakMap;
import io.opentelemetry.auto.bootstrap.instrumentation.java.concurrent.CallableWrapper;
import io.opentelemetry.auto.bootstrap.instrumentation.java.concurrent.RunnableWrapper;
import io.opentelemetry.auto.bootstrap.instrumentation.java.concurrent.State;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
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
    final AgentSpan span = activeSpan();
    return (span != null
        && task != null
        && !ExecutorInstrumentationUtils.isExecutorDisabledForThisTask(executor, task));
  }

  /**
   * Create task state given current scope.
   *
   * @param contextStore context storage
   * @param task task instance
   * @param span current span
   * @param <T> task class type
   * @return new state
   */
  public static <T> State setupState(
      final ContextStore<T, State> contextStore, final T task, final AgentSpan span) {
    final State state = contextStore.putIfAbsent(task, State.FACTORY);
    state.setParentSpan(span);
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
      Note: this may potentially clear somebody else's parent span if we didn't set it
      up in setupState because it was already present before us. This should be safe but
      may lead to non-attributed async work in some very rare cases.
      Alternative is to not clear parent span here if we did not set it up in setupState
      but this may potentially lead to memory leaks if callers do not properly handle
      exceptions.
       */
      state.clearParentSpan();
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
