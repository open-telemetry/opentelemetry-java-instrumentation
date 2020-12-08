/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;

public interface HttpClientOperation<RESPONSE> {

  static <RESPONSE> HttpClientOperation<RESPONSE> noop() {
    return NoopHttpClientOperation.noop();
  }

  Scope makeCurrent();

  /** Used for running user callbacks on completion of the http client operation. */
  Scope makeParentCurrent();

  void end(RESPONSE response);

  void end(RESPONSE response, long endTimeNanos);

  void endExceptionally(Throwable t);

  void endExceptionally(RESPONSE response, Throwable throwable);

  void endExceptionally(RESPONSE response, Throwable throwable, long endTimeNanos);

  /** Convenience method for bytecode instrumentation. */
  default void endMaybeExceptionally(RESPONSE response, Throwable throwable) {
    if (throwable != null) {
      endExceptionally(throwable);
    } else {
      end(response);
    }
  }

  Span getSpan();

  /**
   * Instrumenters should inject context during {@link HttpClientTracer#startOperation} if possible,
   * but some may need to inject context later.
   *
   * <p>This overload uses the globally configured propagators.
   */
  default <REQUEST> void inject(REQUEST request, TextMapPropagator.Setter<REQUEST> setter) {
    inject(request, setter, OpenTelemetry.getGlobalPropagators().getTextMapPropagator());
  }

  /**
   * Instrumenters should inject context during {@link HttpClientTracer#startOperation} if possible,
   * but some may need to inject context later.
   */
  <REQUEST> void inject(
      REQUEST request, TextMapPropagator.Setter<REQUEST> setter, TextMapPropagator propagator);
}
