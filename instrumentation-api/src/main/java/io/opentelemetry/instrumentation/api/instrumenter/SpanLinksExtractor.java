/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;

/** Extractor of span links for a request. */
@FunctionalInterface
public interface SpanLinksExtractor<REQUEST> {

  /**
   * Extracts {@link SpanContext}s that should be linked to the newly created span and adds them to
   * {@code spanLinks}.
   *
   * <p>A link points to a related span other than the parent (for example, the producer span of a
   * message being processed). Implementations should not derive link targets from the span
   * currently in {@code parentContext}; {@code parentContext} is provided for access to
   * request-scoped state attached via {@link io.opentelemetry.context.ContextKey ContextKey}s (for
   * example, baggage set earlier in the pipeline).
   */
  void extract(SpanLinksBuilder spanLinks, Context parentContext, REQUEST request);
}
