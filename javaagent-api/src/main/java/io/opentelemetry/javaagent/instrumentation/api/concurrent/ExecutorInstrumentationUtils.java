/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.concurrent;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.ContextPropagationDebug;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.internal.InstrumentedTaskClasses;

/** Utils for concurrent instrumentations. */
public final class ExecutorInstrumentationUtils {

  /**
   * Checks if given task should get state attached.
   *
   * @param task task object
   * @return true iff given task object should be wrapped
   */
  public static boolean shouldAttachStateToTask(Object task) {
    if (task == null) {
      return false;
    }

    if (Context.current() == Context.root()) {
      // not much point in propagating root context
      // plus it causes failures under otel.javaagent.testing.fail-on-context-leak=true
      return false;
    }

    return InstrumentedTaskClasses.canInstrumentTaskClass(task.getClass());
  }

  /**
   * Create task state given current scope.
   *
   * @param <T> task class type
   * @param contextStore context storage
   * @param task task instance
   * @param context current context
   * @return new state
   */
  public static <T> State setupState(ContextStore<T, State> contextStore, T task, Context context) {
    State state = contextStore.putIfAbsent(task, State.FACTORY);
    if (ContextPropagationDebug.isThreadPropagationDebuggerEnabled()) {
      context =
          ContextPropagationDebug.appendLocations(context, new Exception().getStackTrace(), task);
    }
    state.setParentContext(context);
    return state;
  }

  /**
   * Clean up after job submission method has exited.
   *
   * @param state task instrumentation state
   * @param throwable throwable that may have been thrown
   */
  public static void cleanUpOnMethodExit(State state, Throwable throwable) {
    if (null != state && null != throwable) {
      /*
      Note: this may potentially clear somebody else's parent span if we didn't set it
      up in setupState because it was already present before us. This should be safe but
      may lead to non-attributed async work in some very rare cases.
      Alternative is to not clear parent span here if we did not set it up in setupState
      but this may potentially lead to memory leaks if callers do not properly handle
      exceptions.
       */
      state.clearParentContext();
    }
  }

  private ExecutorInstrumentationUtils() {}
}
