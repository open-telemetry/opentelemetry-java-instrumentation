/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import java.time.Instant;

/**
 * Extractor of the start time of request processing. A {@link StartTimeExtractor} should always use
 * the same timestamp source as the corresponding {@link EndTimeExtractor} - extracted timestamps
 * must be comparable.
 */
@FunctionalInterface
public interface StartTimeExtractor<REQUEST> {

  /** Returns the timestamp marking the start of the request processing. */
  Instant extract(REQUEST request);
}
