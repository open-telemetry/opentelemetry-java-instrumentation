/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import static java.util.Collections.emptySet;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.metrics.Meter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * Configuration holder for JFR telemetry. On Java versions prior to 17, this is a no-op
 * implementation since JFR-based metrics are not supported. On Java 17+, this is replaced by an
 * implementation that manages JFR metrics.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class JfrConfig {

  public static JfrConfig create() {
    return new JfrConfig();
  }

  @CanIgnoreReturnValue
  public JfrConfig setUseLegacyJfrCpuCountMetric(boolean useLegacy) {
    return this;
  }

  public JfrTelemetry buildJfrTelemetry(
      Predicate<String> metricNamePredicate,
      Meter meter,
      boolean requireCompleteJmxReplacement,
      boolean emitExperimentalJmxMetrics) {
    return new JfrTelemetry(null, emptySet());
  }

  /**
   * JFR telemetry and the metric names it registered.
   *
   * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
   * at any time.
   */
  public static final class JfrTelemetry {
    @Nullable private final AutoCloseable telemetry;
    private final Set<String> metricNames;

    public JfrTelemetry(@Nullable AutoCloseable telemetry, Set<String> metricNames) {
      this.telemetry = telemetry;
      this.metricNames = Collections.unmodifiableSet(new HashSet<>(metricNames));
    }

    @Nullable
    public AutoCloseable getTelemetry() {
      return telemetry;
    }

    public Set<String> getMetricNames() {
      return metricNames;
    }
  }

  private JfrConfig() {}
}
