/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import java.time.Instant;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Extractor of the start time of request processing. A {@link StartTimeExtractor} should always use
 * the same timestamp source as the corresponding {@link EndTimeExtractor} - extracted timestamps
 * must be comparable.
 */
@FunctionalInterface
public interface StartTimeExtractor<REQUEST> {

  /**
   * Returns the default {@link StartTimeExtractor}, which delegates to the OpenTelemetry SDK
   * internal clock.
   */
  static <REQUEST> StartTimeExtractor<REQUEST> getDefault() {
    return request -> null;
  }

  /**
   * Returns the timestamp marking the start of the request processing. If the returned timestamp is
   * {@code null} the OpenTelemetry SDK will use its internal clock to determine the start time.
   */
  @Nullable
  Instant extract(REQUEST request);
}
