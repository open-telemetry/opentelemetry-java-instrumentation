/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

/**
 * Extractor of the span name for a request. Where possible, an extractor based on semantic
 * conventions returned from the factories in this class should be used. The most common reason to
 * provide a custom implementation would be to apply the extractor for a particular semantic
 * convention by first determining which convention applies to the request.
 */
@FunctionalInterface
public interface SpanNameExtractor<REQUEST> {

  /** Returns the span name. */
  String extract(REQUEST request);
}
