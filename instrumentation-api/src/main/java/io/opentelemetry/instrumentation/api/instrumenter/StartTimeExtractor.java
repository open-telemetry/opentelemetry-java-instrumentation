/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import java.time.Instant;

/** Extractor of the start time of request processing. */
public interface StartTimeExtractor<REQUEST> {

  /**
   * Returns the default {@link StartTimeExtractor}, which always returns the current time with
   * nanosecond precision.
   */
  static <REQUEST> StartTimeExtractor<REQUEST> getDefault() {
    return request -> CurrentNanoTime.get();
  }

  /** Returns the timestamp marking the start of the request processing. */
  Instant extract(REQUEST request);
}
