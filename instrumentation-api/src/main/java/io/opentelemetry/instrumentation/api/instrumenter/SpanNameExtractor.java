/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

/** Extractor of the span name for a request. */
@FunctionalInterface
public interface SpanNameExtractor<REQUEST> {

  /** Returns the span name. */
  String extract(REQUEST request);
}
