/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;

public interface HttpClientOperation {

  static HttpClientOperation noop() {
    return NoopHttpClientOperation.noop();
  }

  static HttpClientOperation create(Context context, Context parentContext) {
    return new DefaultHttpClientOperation(context, parentContext);
  }

  Scope makeCurrent();

  /** Used for running user callbacks on completion of the http client operation. */
  Scope makeParentCurrent();

  Span getSpan();

  <C> void inject(TextMapPropagator propagator, C carrier, TextMapPropagator.Setter<C> setter);

  // TODO (trask) how to provide general access to context, but still no-op correctly?
  //  maybe something like
  //  - void doWithContext(Consumer<Context>)
  //  - T doWithContext(Function<Context, R>)
}
