/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import java.time.Instant;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Extractor of the end time of response processing. An {@link EndTimeExtractor} should always use
 * the same timestamp source as the corresponding {@link StartTimeExtractor} - extracted timestamps
 * must be comparable.
 */
@FunctionalInterface
public interface EndTimeExtractor<RESPONSE> {

  /**
   * Returns the default {@link EndTimeExtractor}, which delegates to the OpenTelemetry SDK internal
   * clock.
   */
  static <RESPONSE> EndTimeExtractor<RESPONSE> getDefault() {
    return response -> null;
  }

  /**
   * Returns the timestamp marking the end of the response processing. If the returned timestamp is
   * {@code null} the OpenTelemetry SDK will use its internal clock to determine the end time.
   */
  @Nullable
  Instant extract(RESPONSE response);
}
