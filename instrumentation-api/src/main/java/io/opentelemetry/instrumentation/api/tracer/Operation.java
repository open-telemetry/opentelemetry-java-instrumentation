/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;

public interface Operation<RESULT> {

  static <RESULT> Operation<RESULT> noop() {
    return NoopOperation.noop();
  }

  static <RESULT> Operation<RESULT> create(
      Context context, Context parentContext, HttpClientTracer<?, RESULT> tracer) {
    return new DefaultOperation<>(context, parentContext, tracer);
  }

  Scope makeCurrent();

  /** Used for running user callbacks on completion of the http client operation. */
  Scope makeParentCurrent();

  /**
   * Convenience method for {@link #end(Object, long)} which has no result, and uses the current
   * time.
   */
  void end();

  /** Convenience method for {@link #end(Object, long)} which uses the current time. */
  void end(RESULT result);

  void end(RESULT result, long endTimeNanos);

  /**
   * Convenience method for {@link #endExceptionally(Throwable, Object, long)} which has no result,
   * and uses the current time.
   */
  void endExceptionally(Throwable throwable);

  /**
   * Convenience method for {@link #endExceptionally(Throwable, Object, long)} which uses the
   * current time.
   */
  void endExceptionally(Throwable throwable, RESULT result);

  void endExceptionally(Throwable throwable, RESULT result, long endTimeNanos);

  /** Convenience method for bytecode instrumentation. */
  default void endMaybeExceptionally(RESULT result, Throwable throwable) {
    if (throwable != null) {
      endExceptionally(throwable);
    } else {
      end(result);
    }
  }

  Span getSpan();

  <C> void inject(TextMapPropagator propagator, C carrier, TextMapPropagator.Setter<C> setter);

  // TODO (trask) how to provide general access to context, but still no-op correctly?
  //  maybe something like
  //  - void doWithContext(Consumer<Context>)
  //  - T doWithContext(Function<Context, R>)
}
