/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import io.opentelemetry.instrumentation.runtimetelemetry.RuntimeTelemetryBuilder;
import java.util.List;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

/**
 * This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 */
public final class Experimental {

  @Nullable
  private static volatile BiConsumer<RuntimeTelemetryBuilder, Boolean> setEmitExperimentalMetrics;

  @Nullable
  private static volatile BiConsumer<RuntimeTelemetryBuilder, Boolean>
      setEmitExperimentalJfrMetrics;

  @Nullable private static volatile BiConsumer<RuntimeTelemetryBuilder, List<String>> setJfrMetrics;

  /**
   * Sets whether experimental JMX-based metrics should be emitted. Experimental metrics are those
   * not marked as stable in the <a
   * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/runtime/jvm-metrics.md">semantic
   * conventions</a>.
   *
   * @param builder the runtime telemetry builder
   * @param emitExperimentalMetrics {@code true} to emit experimental JMX metrics
   */
  public static void setEmitExperimentalMetrics(
      RuntimeTelemetryBuilder builder, boolean emitExperimentalMetrics) {
    if (setEmitExperimentalMetrics != null) {
      setEmitExperimentalMetrics.accept(builder, emitExperimentalMetrics);
    }
  }

  public static void internalSetEmitExperimentalMetrics(
      BiConsumer<RuntimeTelemetryBuilder, Boolean> setEmitExperimentalMetrics) {
    Experimental.setEmitExperimentalMetrics = setEmitExperimentalMetrics;
  }

  /**
   * Sets whether experimental JFR-based metrics should be emitted (Java 17+). Experimental metrics
   * are those not marked as stable in the <a
   * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/runtime/jvm-metrics.md">semantic
   * conventions</a>.
   *
   * @param builder the runtime telemetry builder
   * @param emitExperimentalJfrMetrics {@code true} to emit experimental JFR metrics
   */
  public static void setEmitExperimentalJfrMetrics(
      RuntimeTelemetryBuilder builder, boolean emitExperimentalJfrMetrics) {
    if (setEmitExperimentalJfrMetrics != null) {
      setEmitExperimentalJfrMetrics.accept(builder, emitExperimentalJfrMetrics);
    }
  }

  public static void internalSetEmitExperimentalJfrMetrics(
      BiConsumer<RuntimeTelemetryBuilder, Boolean> setEmitExperimentalJfrMetrics) {
    Experimental.setEmitExperimentalJfrMetrics = setEmitExperimentalJfrMetrics;
  }

  /**
   * Selects the metrics to source from JFR on Java 17+.
   *
   * <p>Each pattern must be an exact metric name or a prefix ending in {@code *}. Metrics that JFR
   * actually registers are suppressed from JMX to avoid duplicates. This method is a no-op on Java
   * versions prior to 17.
   *
   * @param builder the runtime telemetry builder
   * @param metricNamePatterns metric name patterns to source from JFR
   */
  public static void setJfrMetrics(
      RuntimeTelemetryBuilder builder, List<String> metricNamePatterns) {
    if (setJfrMetrics != null) {
      setJfrMetrics.accept(builder, metricNamePatterns);
    }
  }

  public static void internalSetJfrMetrics(
      BiConsumer<RuntimeTelemetryBuilder, List<String>> setJfrMetrics) {
    Experimental.setJfrMetrics = setJfrMetrics;
  }

  private Experimental() {}
}
