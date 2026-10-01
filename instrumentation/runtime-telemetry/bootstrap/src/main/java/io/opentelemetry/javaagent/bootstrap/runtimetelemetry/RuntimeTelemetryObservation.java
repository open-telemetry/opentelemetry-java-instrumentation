/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.runtimetelemetry;

import static java.util.Collections.emptySet;

import io.opentelemetry.instrumentation.api.internal.Initializer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Shares which JMX metrics the agent's runtime telemetry registered with instrumentations loaded in
 * other class loaders, so that they can avoid reporting the same observations twice.
 */
public final class RuntimeTelemetryObservation {
  private static volatile Set<String> registeredJmxObservers = emptySet();

  /**
   * Returns the names of the JMX metrics whose observers were registered, or an empty set before
   * runtime telemetry is installed. Registration does not guarantee that a metric is exported, for
   * example when an SDK view drops it.
   *
   * <p>Reports reviewed class-loading, memory, buffer, CPU, thread and GC metrics. JFR-only
   * replacements are not included.
   */
  public static Set<String> registeredJmxObservers() {
    return registeredJmxObservers;
  }

  @Initializer
  public static void internalSetRegisteredJmxObservers(Set<String> metricNames) {
    registeredJmxObservers = Collections.unmodifiableSet(new HashSet<>(metricNames));
  }

  private RuntimeTelemetryObservation() {}
}
