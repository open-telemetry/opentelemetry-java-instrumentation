/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17.internal;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DurationUtil {
  private static final double NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

  /** Returns the duration as seconds, with fractional part included. */
  public static double toSeconds(Duration duration) {
    double epochSecs = (double) duration.getSeconds();
    return epochSecs + duration.getNano() / NANOS_PER_SECOND;
  }

  private DurationUtil() {}
}
