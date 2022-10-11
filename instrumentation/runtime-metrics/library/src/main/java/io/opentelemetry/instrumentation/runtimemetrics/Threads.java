/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

/**
 * Registers measurements that generate metrics about JVM threads.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * Threads.registerObservers(GlobalOpenTelemetry.get());
 * }</pre>
 *
 * <p>Example metrics being exported:
 *
 * <pre>
 *   process.runtime.jvm.threads.count{daemon=true} 2
 *   process.runtime.jvm.threads.count{daemon=false} 5
 * </pre>
 */
public final class Threads {

  // Visible for testing
  static final Threads INSTANCE = new Threads();

  static final AttributeKey<Boolean> DAEMON = AttributeKey.booleanKey("daemon");

  /** Register observers for java runtime class metrics. */
  public static void registerObservers(OpenTelemetry openTelemetry) {
    INSTANCE.registerObservers(openTelemetry, ManagementFactory.getThreadMXBean());
  }

  // Visible for testing
  void registerObservers(OpenTelemetry openTelemetry, ThreadMXBean threadBean) {
    Meter meter = openTelemetry.getMeter("io.opentelemetry.runtime-metrics");

    meter
        .upDownCounterBuilder("process.runtime.jvm.threads.count")
        .setDescription("Number of executing threads")
        .setUnit("1")
        .buildWithCallback(
            observableMeasurement -> {
              observableMeasurement.record(
                  threadBean.getDaemonThreadCount(),
                  Attributes.builder().put(DAEMON, true).build());
              observableMeasurement.record(
                  threadBean.getThreadCount() - threadBean.getDaemonThreadCount(),
                  Attributes.builder().put(DAEMON, false).build());
            });
  }

  private Threads() {}
}
