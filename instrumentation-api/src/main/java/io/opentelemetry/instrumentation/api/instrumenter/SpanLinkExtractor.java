/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;

/** Extractor of a span link for a request. */
@FunctionalInterface
public interface SpanLinkExtractor<REQUEST> {
  /**
   * Extract a {@link SpanContext} that should be linked to the newly created span. Returning {@code
   * SpanContext.getInvalid()} will not add any link to the span.
   */
  SpanContext extract(Context parentContext, REQUEST request);

  /**
   * Returns a new {@link SpanLinkExtractor} that will extract a {@link SpanContext} from the
   * request using configured {@code propagators}.
   */
  static <REQUEST> SpanLinkExtractor<REQUEST> fromUpstreamRequest(
      ContextPropagators propagators, TextMapGetter<REQUEST> getter) {
    return new PropagatorBasedSpanLinkExtractor<>(propagators, getter);
  }
}
