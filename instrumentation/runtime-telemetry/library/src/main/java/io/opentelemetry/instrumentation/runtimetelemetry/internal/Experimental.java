/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import io.opentelemetry.instrumentation.api.config.IncludeExclude;
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

  /**
   * The metrics that JFR and JMX both implement. These are exactly the metrics selected by the
   * deprecated prefer-jfr option. Incomplete JFR implementations may remain sourced from JMX.
   */
  public static final List<String> JMX_OVERLAPPING_JFR_METRICS =
      unmodifiableList(
          asList(
              "jvm.buffer.count",
              "jvm.buffer.memory.limit",
              "jvm.buffer.memory.used",
              "jvm.class.count",
              "jvm.class.loaded",
              "jvm.class.unloaded",
              "jvm.cpu.count",
              "jvm.cpu.limit",
              "jvm.cpu.recent_utilization",
              "jvm.gc.duration",
              "jvm.memory.committed",
              "jvm.memory.init",
              "jvm.memory.limit",
              "jvm.memory.used",
              "jvm.memory.used_after_last_gc",
              "jvm.system.cpu.utilization",
              "jvm.thread.count"));

  @Nullable
  private static volatile BiConsumer<RuntimeTelemetryBuilder, Boolean> setEmitExperimentalMetrics;

  @Nullable
  private static volatile BiConsumer<RuntimeTelemetryBuilder, Boolean>
      setEmitExperimentalJfrMetrics;

  @Nullable
  private static volatile BiConsumer<RuntimeTelemetryBuilder, IncludeExclude> setJfrMetrics;

  @Nullable
  private static volatile BiConsumer<RuntimeTelemetryBuilder, Boolean> setPreferJfrMetrics;

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
   * <p>Metric names and selector patterns are matched case-sensitively. {@code ?} matches any
   * single character and {@code *} matches any number of characters, including none. Excluded
   * patterns take precedence over included patterns. JFR is inactive unless a selector is
   * configured; a selector with only excluded patterns sources every metric that JFR implements and
   * that it does not exclude.
   *
   * <p>Metrics that JFR actually registers are suppressed from JMX to avoid duplicates. This method
   * is a no-op on Java versions prior to 17.
   *
   * @param builder the runtime telemetry builder
   * @param selector metric names to source from JFR
   */
  public static void setJfrMetrics(RuntimeTelemetryBuilder builder, IncludeExclude selector) {
    if (setJfrMetrics != null) {
      setJfrMetrics.accept(builder, selector);
    }
  }

  public static void internalSetJfrMetrics(
      BiConsumer<RuntimeTelemetryBuilder, IncludeExclude> setJfrMetrics) {
    Experimental.setJfrMetrics = setJfrMetrics;
  }

  public static void internalSetPreferJfrMetrics(
      BiConsumer<RuntimeTelemetryBuilder, Boolean> setPreferJfrMetrics) {
    Experimental.setPreferJfrMetrics = setPreferJfrMetrics;
  }

  /**
   * Sets whether to prefer JFR over JMX for metrics where both collection methods are available.
   *
   * @param builder the runtime telemetry builder
   * @param preferJfrMetrics {@code true} to prefer JFR over JMX where both are available
   * @deprecated Use {@link #setJfrMetrics(RuntimeTelemetryBuilder, IncludeExclude)} instead,
   *     passing the metric names to source from JFR. May be removed in the next minor release.
   */
  @Deprecated // may be removed in the next minor release
  public static void setPreferJfrMetrics(
      RuntimeTelemetryBuilder builder, boolean preferJfrMetrics) {
    if (setPreferJfrMetrics != null) {
      setPreferJfrMetrics.accept(builder, preferJfrMetrics);
    }
  }

  private Experimental() {}
}
