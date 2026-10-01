/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

import io.micrometer.core.instrument.Meter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Selects the Micrometer {@code ClassLoaderMetrics} meters that should not be bridged because the
 * agent's runtime telemetry already reports the same JMX observations.
 */
final class JvmMetricsOwnership implements Predicate<Meter.Id> {
  private static final Logger logger = Logger.getLogger(JvmMetricsOwnership.class.getName());

  private final boolean enabled;
  private final Set<String> kept;
  private final Set<String> registeredObservers;

  JvmMetricsOwnership(boolean enabled, Set<String> kept, Set<String> registeredObservers) {
    this.enabled = enabled;
    this.kept = Collections.unmodifiableSet(new HashSet<>(kept));
    this.registeredObservers = Collections.unmodifiableSet(new HashSet<>(registeredObservers));
    if (enabled && registeredObservers.isEmpty()) {
      // runtime telemetry is disabled, reports these metrics through JFR, or has not been
      // installed yet (agent listeners are delayed when the application uses a custom LogManager)
      logger.log(
          WARNING,
          "Micrometer JVM metrics ownership is enabled, but runtime telemetry has not registered"
              + " any JMX class-loading metrics; all Micrometer meters will be bridged.");
    }
  }

  @Override
  public boolean test(Meter.Id id) {
    if (!enabled || kept.contains(id.getName())) {
      return false;
    }
    String replacement = replacement(id);
    if (replacement == null || !registeredObservers.contains(replacement)) {
      return false;
    }
    logger.log(
        FINE,
        "Not bridging Micrometer meter {0}, runtime telemetry reports {1}",
        new Object[] {id.getName(), replacement});
    return true;
  }

  @Nullable
  private static String replacement(Meter.Id id) {
    // ClassLoaderMetrics (with either the Micrometer or the OpenTelemetry naming convention) reads
    // the same ClassLoadingMXBean values as runtime telemetry's Classes. Function counters have
    // Meter.Type.COUNTER.
    switch (id.getName()) {
      case "jvm.classes.loaded":
      case "jvm.class.count":
        return id.getType() == Meter.Type.GAUGE ? "jvm.class.count" : null;
      case "jvm.classes.loaded.count":
      case "jvm.class.loaded":
        return id.getType() == Meter.Type.COUNTER ? "jvm.class.loaded" : null;
      case "jvm.classes.unloaded":
      case "jvm.class.unloaded":
        return id.getType() == Meter.Type.COUNTER ? "jvm.class.unloaded" : null;
      default:
        return null;
    }
  }
}
