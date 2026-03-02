/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

/**
 * Enumeration of JFR features, used internally to control which JFR events are registered.
 *
 * <p>Features that overlap with stable JMX-based instrumentation are disabled by default to avoid
 * duplicate metrics. Experimental features (those not marked stable in the semantic conventions)
 * are also disabled by default and require {@code emit_experimental_metrics=true} to enable.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public enum JfrFeature {
  BUFFER_METRICS(/* overlapsWithJmx= */ true, /* experimental= */ true),
  CLASS_LOAD_METRICS(/* overlapsWithJmx= */ true, /* experimental= */ false),
  CONTEXT_SWITCH_METRICS(/* overlapsWithJmx= */ false, /* experimental= */ true),
  CPU_COUNT_METRICS(/* overlapsWithJmx= */ true, /* experimental= */ false),
  CPU_UTILIZATION_METRICS(/* overlapsWithJmx= */ true, /* experimental= */ false),
  GC_DURATION_METRICS(/* overlapsWithJmx= */ true, /* experimental= */ false),
  LOCK_METRICS(/* overlapsWithJmx= */ false, /* experimental= */ true),
  MEMORY_ALLOCATION_METRICS(/* overlapsWithJmx= */ false, /* experimental= */ true),
  MEMORY_POOL_METRICS(/* overlapsWithJmx= */ true, /* experimental= */ false),
  NETWORK_IO_METRICS(/* overlapsWithJmx= */ false, /* experimental= */ true),
  THREAD_METRICS(/* overlapsWithJmx= */ true, /* experimental= */ false),
  ;

  private final boolean overlapsWithJmx;
  private final boolean experimental;

  JfrFeature(boolean overlapsWithJmx, boolean experimental) {
    this.overlapsWithJmx = overlapsWithJmx;
    this.experimental = experimental;
  }

  /** Returns true if this JFR feature overlaps with JMX-based metrics. */
  public boolean overlapsWithJmx() {
    return overlapsWithJmx;
  }

  /** Returns true if this JFR feature produces experimental (non-stable) metrics. */
  public boolean isExperimental() {
    return experimental;
  }
}
