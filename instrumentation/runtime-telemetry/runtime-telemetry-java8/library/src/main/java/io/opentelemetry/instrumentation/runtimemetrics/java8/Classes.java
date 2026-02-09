/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8;

import io.opentelemetry.api.OpenTelemetry;
import java.util.List;

/**
 * Registers measurements that generate metrics about JVM classes.
 *
 * @deprecated Use {@link RuntimeMetrics} instead, and configure metric views to select specific
 *     metrics.
 */
@Deprecated
public final class Classes {

  /** Register observers for java runtime class metrics. */
  public static List<AutoCloseable> registerObservers(OpenTelemetry openTelemetry) {
    return io.opentelemetry.instrumentation.runtimemetrics.java8.internal.Classes.registerObservers(
        openTelemetry);
  }

  private Classes() {}
}
