/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.api.concurrent;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.ContextUtils;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.auto.api.ContextStore;
import io.opentelemetry.trace.Tracer;

/** Helper utils for Runnable/Callable instrumentation */
public class AdviceUtils {

  public static final Tracer TRACER =
      OpenTelemetry.getTracer("io.opentelemetry.auto.java-concurrent");

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
        return ContextUtils.withScopedContext(parentContext);
      }
    }
    return null;
  }
}
