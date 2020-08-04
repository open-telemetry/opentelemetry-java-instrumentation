/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.api.concurrent;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.ContextUtils;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.ContextStore;
import io.opentelemetry.trace.Tracer;

/** Helper utils for Runnable/Callable instrumentation */
public class AdviceUtils {

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.java-concurrent");

  /**
   * Start scope for a given task
   *
   * @param contextStore context storage for task's state
   * @param task task to start scope for
   * @param <T> task's type
   * @return scope if scope was started, or null
   */
  public static <T> Scope startTaskScope(final ContextStore<T, State> contextStore, final T task) {
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
