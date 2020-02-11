package io.opentelemetry.auto.instrumentation.java.concurrent;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.bootstrap.instrumentation.java.concurrent.State;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import lombok.extern.slf4j.Slf4j;

/** Helper utils for Runnable/Callable instrumentation */
@Slf4j
public class AdviceUtils {

  public static Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.java-concurrent");

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
      final Span parentSpan = state.getAndResetParentSpan();
      if (parentSpan != null) {
        return new SpanWithScope(parentSpan, TRACER.withSpan(parentSpan));
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
