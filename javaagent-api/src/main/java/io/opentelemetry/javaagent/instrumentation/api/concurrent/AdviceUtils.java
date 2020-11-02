/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.concurrent;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;

/** Helper utils for Runnable/Callable instrumentation */
public class AdviceUtils {

  private static final Tracer TRACER =
      OpenTelemetry.getGlobalTracer("io.opentelemetry.auto.java-concurrent");

  public static Tracer tracer() {
    return TRACER;
  }

  /**
   * Start scope for a given task
   *
   * @param contextStore context storage for task's state
   * @param task task to start scope for
   * @param <T> task's type
   * @return scope if scope was started, or null
   */
  public static <T> Scope startTaskScope(ContextStore<T, State> contextStore, T task) {
    State state = contextStore.get(task);
    if (state != null) {
      Context parentContext = state.getAndResetParentContext();
      if (parentContext != null) {
        return parentContext.makeCurrent();
      }
    }
    return null;
  }
}
