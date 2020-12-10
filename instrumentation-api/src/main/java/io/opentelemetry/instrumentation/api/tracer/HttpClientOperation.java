/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;

public interface HttpClientOperation<RESPONSE> {

  static <RESPONSE> HttpClientOperation<RESPONSE> noop() {
    return NoopHttpClientOperation.noop();
  }

  static <RESPONSE> HttpClientOperation<RESPONSE> create(
      Context context, Context parentContext, HttpClientTracer<?, RESPONSE> tracer) {
    return new DefaultHttpClientOperation<>(context, parentContext, tracer);
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
  void end(RESPONSE response);

  void end(RESPONSE response, long endTimeNanos);

  /**
   * Convenience method for {@link #endExceptionally(Throwable, Object, long)} which has no result,
   * and uses the current time.
   */
  void endExceptionally(Throwable throwable);

  /**
   * Convenience method for {@link #endExceptionally(Throwable, Object, long)} which uses the
   * current time.
   */
  void endExceptionally(Throwable throwable, RESPONSE response);

  void endExceptionally(Throwable throwable, RESPONSE response, long endTimeNanos);

  /** Convenience method for bytecode instrumentation. */
  default void endMaybeExceptionally(RESPONSE response, Throwable throwable) {
    if (throwable != null) {
      endExceptionally(throwable);
    } else {
      end(response);
    }
  }

  Span getSpan();

  <C> void inject(TextMapPropagator propagator, C carrier, TextMapPropagator.Setter<C> setter);

  // TODO (trask) how to provide general access to context, but still no-op correctly?
  //  maybe something like
  //  - void doWithContext(Consumer<Context>)
  //  - T doWithContext(Function<Context, R>)
}
