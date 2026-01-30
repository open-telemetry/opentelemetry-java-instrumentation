/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8;

import io.opentelemetry.api.OpenTelemetry;
import java.util.List;

/**
 * Registers instruments that generate metrics about JVM garbage collection.
 *
 * @deprecated Use {@link RuntimeMetrics} instead, and configure metric views to select specific
 *     metrics.
 */
@Deprecated
public final class GarbageCollector {

  /** Register observers for java runtime memory metrics. */
  public static List<AutoCloseable> registerObservers(
      OpenTelemetry openTelemetry, boolean captureGcCause) {
    return io.opentelemetry.instrumentation.runtimemetrics.java8.internal.GarbageCollector
        .registerObservers(openTelemetry, captureGcCause);
  }

  private GarbageCollector() {}
}
