/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetryjfr;

/**
 * Enumeration of JFR features, which can be toggled on or off via {@link JfrTelemetryBuilder}.
 *
 * <p>Features are disabled by default if they are already available through {@code
 * io.opentelemetry.instrumentation:opentelemetry-runtime-metrics} JMX based instrumentation.
 */
public enum JfrFeature {
  BUFFER_METRICS(/* defaultEnabled= */ false),
  CLASS_LOAD_METRICS(/* defaultEnabled= */ false),
  CONTEXT_SWITCH_METRICS(/* defaultEnabled= */ true),
  CPU_COUNT_METRICS(/* defaultEnabled= */ true),
  CPU_UTILIZATION_METRICS(/* defaultEnabled= */ false),
  GC_DURATION_METRICS(/* defaultEnabled= */ false),
  LOCK_METRICS(/* defaultEnabled= */ true),
  MEMORY_ALLOCATION_METRICS(/* defaultEnabled= */ true),
  MEMORY_POOL_METRICS(/* defaultEnabled= */ false),
  NETWORK_IO_METRICS(/* defaultEnabled= */ true),
  THREAD_METRICS(/* defaultEnabled= */ false),
  ;

  private final boolean defaultEnabled;

  JfrFeature(boolean defaultEnabled) {
    this.defaultEnabled = defaultEnabled;
  }

  boolean isDefaultEnabled() {
    return defaultEnabled;
  }
}
