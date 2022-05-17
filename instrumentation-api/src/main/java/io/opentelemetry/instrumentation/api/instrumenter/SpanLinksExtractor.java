/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;

/** Extractor of span links for a request. */
@FunctionalInterface
public interface SpanLinksExtractor<REQUEST> {

  /**
   * Extracts {@link SpanContext}s that should be linked to the newly created span and adds them to
   * {@code spanLinks}.
   */
  void extract(SpanLinksBuilder spanLinks, Context parentContext, REQUEST request);

  /**
   * Returns a new {@link SpanLinksExtractor} that will extract a {@link SpanContext} from the
   * request using configured {@link TextMapPropagator}.
   */
  static <REQUEST> SpanLinksExtractor<REQUEST> extractFromRequest(
      TextMapPropagator propagator, TextMapGetter<REQUEST> getter) {
    return new PropagatorBasedSpanLinksExtractor<>(propagator, getter);
  }
}
