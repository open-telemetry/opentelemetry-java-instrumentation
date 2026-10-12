/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1.internal;

import io.opentelemetry.instrumentation.lettuce.v5_1.LettuceTelemetryBuilder;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

/**
 * This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 */
public final class Experimental {

  @Nullable
  private static volatile BiConsumer<LettuceTelemetryBuilder, Boolean> setEncodingSpanEventsEnabled;

  /**
   * Sets whether the {@code redis.encode.start} and {@code redis.encode.end} span events should be
   * emitted. Disabled by default.
   */
  public static void setEncodingSpanEventsEnabled(
      LettuceTelemetryBuilder builder, boolean encodingEventsEnabled) {
    if (setEncodingSpanEventsEnabled != null) {
      setEncodingSpanEventsEnabled.accept(builder, encodingEventsEnabled);
    }
  }

  public static void internalSetEncodingSpanEventsEnabled(
      BiConsumer<LettuceTelemetryBuilder, Boolean> setEncodingSpanEventsEnabled) {
    Experimental.setEncodingSpanEventsEnabled = setEncodingSpanEventsEnabled;
  }

  private Experimental() {}
}
