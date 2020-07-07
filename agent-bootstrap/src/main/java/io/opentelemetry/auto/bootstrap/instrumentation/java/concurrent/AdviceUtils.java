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

package io.opentelemetry.auto.bootstrap.instrumentation.java.concurrent;

import static io.opentelemetry.trace.TracingContextUtils.getSpan;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.context.ContextUtils;
import io.opentelemetry.trace.Tracer;
import lombok.extern.slf4j.Slf4j;

/** Helper utils for Runnable/Callable instrumentation */
@Slf4j
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
  public static <T> SpanWithScope startTaskScope(
      final ContextStore<T, State> contextStore, final T task) {
    final State state = contextStore.get(task);
    if (state != null) {
      final Context parentContext = state.getAndResetParentContext();
      if (parentContext != null) {
        return new SpanWithScope(
            getSpan(parentContext), ContextUtils.withScopedContext(parentContext));
      }
    }
    return null;
  }

  public static void endTaskScope(final SpanWithScope spanWithScope) {
    if (spanWithScope != null) {
      spanWithScope.closeScope();
    }
  }
}
