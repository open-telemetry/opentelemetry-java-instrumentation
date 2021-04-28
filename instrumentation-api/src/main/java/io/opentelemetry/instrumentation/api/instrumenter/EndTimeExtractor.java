/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import java.time.Instant;

/** Extractor of the end time of response processing. */
public interface EndTimeExtractor<RESPONSE> {

  /**
   * Returns the default {@link EndTimeExtractor}, which always returns the current time with
   * nanosecond precision.
   */
  static <RESPONSE> EndTimeExtractor<RESPONSE> getDefault() {
    return response -> CurrentNanoTime.get();
  }

  /** Returns the timestamp marking the end of the response processing. */
  Instant extract(RESPONSE response);
}
