/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.concurrent;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.instrumentation.api.internal.ContextPropagationDebug;
import io.opentelemetry.javaagent.instrumentation.api.internal.InstrumentedTaskClasses;
import java.util.concurrent.ExecutorService;
import javax.annotation.Nullable;

/**
 * Advice helper methods for concurrent executor (e.g. {@link ExecutorService}) instrumentations.
 */
public final class ExecutorAdviceHelper {

  /**
   * Check if {@code context} should be propagated to the passed {@code task}. This method must be
   * called before each {@link #attachContextToTask(Context, VirtualField, Object)} call to ensure
   * that unwanted tasks are not instrumented.
   */
  public static boolean shouldPropagateContext(Context context, @Nullable Object task) {
    if (task == null) {
      return false;
    }

    if (context == Context.root()) {
      // not much point in propagating root context
      // plus it causes failures under otel.javaagent.testing.fail-on-context-leak=true
      return false;
    }

    return InstrumentedTaskClasses.canInstrumentTaskClass(task.getClass());
  }

  /**
   * Associate {@code context} with passed {@code task} using {@code virtualField}. Once the context
   * is attached, {@link TaskAdviceHelper} can be used to make that context current during {@code
   * task} execution.
   */
  public static <T> PropagatedContext attachContextToTask(
      Context context, VirtualField<T, PropagatedContext> virtualField, T task) {

    // note that this is not an atomic operation and one PropagatedContext may overwrite another if
    // the task is submitted to >1 executors at roughly the same time; but we're perfectly fine with
    // that happening - in the event of this happening one of those tasks would lose the original
    // context anyway
    PropagatedContext propagatedContext = virtualField.get(task);
    if (propagatedContext == null) {
      propagatedContext = new PropagatedContext();
      virtualField.set(task, propagatedContext);
    }

    if (ContextPropagationDebug.isThreadPropagationDebuggerEnabled()) {
      context =
          ContextPropagationDebug.appendLocations(context, new Exception().getStackTrace(), task);
    }
    propagatedContext.setContext(context);
    return propagatedContext;
  }

  /**
   * Clean up {@code propagatedContext} in case of any submission errors. Call this method after the
   * submission method has exited.
   */
  public static void cleanUpAfterSubmit(
      @Nullable PropagatedContext propagatedContext, @Nullable Throwable throwable) {
    if (propagatedContext != null && throwable != null) {
      /*
      Note: this may potentially clear somebody else's parent span if we didn't set it
      up in setupState because it was already present before us. This should be safe but
      may lead to non-attributed async work in some very rare cases.
      Alternative is to not clear parent span here if we did not set it up in setupState
      but this may potentially lead to memory leaks if callers do not properly handle
      exceptions.
       */
      propagatedContext.clear();
    }
  }

  /** Clean context attached to the given task. */
  public static <T> void cleanPropagatedContext(
      VirtualField<T, PropagatedContext> virtualField, T task) {
    PropagatedContext propagatedContext = virtualField.get(task);
    if (propagatedContext != null) {
      propagatedContext.clear();
    }
  }

  private ExecutorAdviceHelper() {}
}
