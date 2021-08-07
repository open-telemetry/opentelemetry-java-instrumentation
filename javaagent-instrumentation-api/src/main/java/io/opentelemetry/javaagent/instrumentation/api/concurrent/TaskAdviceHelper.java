/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.concurrent;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Advice helper methods for concurrent task (e.g. {@link Runnable}) instrumentations. */
public final class TaskAdviceHelper {

  /**
   * Make the {@link PropagatedContext} associated with this {@code task} current and return the
   * resulting scope. Will return {@code null} if there's no context attached to this {@code task}.
   */
  @Nullable
  public static <T> Scope makePropagatedContextCurrent(
      ContextStore<T, PropagatedContext> contextStore, T task) {
    PropagatedContext propagatedContext = contextStore.get(task);
    if (propagatedContext != null) {
      Context context = propagatedContext.getAndClear();
      if (context != null) {
        return context.makeCurrent();
      }
    }
    return null;
  }

  private TaskAdviceHelper() {}
}
