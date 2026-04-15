/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import io.opentelemetry.instrumentation.runtimetelemetry.RuntimeTelemetryBuilder;
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
  private static volatile BiConsumer<RuntimeTelemetryBuilder, Boolean> setPreferJfrMetrics;

  /**
   * Sets whether experimental metrics should be emitted. Experimental metrics are those not marked
   * as stable in the <a
   * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/runtime/jvm-metrics.md">semantic
   * conventions</a>.
   *
   * @param builder the runtime telemetry builder
   * @param emitExperimentalMetrics {@code true} to emit experimental metrics
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
   * Sets whether to prefer JFR over JMX for metrics where both collection methods are available.
   * When set to {@code true}, metrics available from both sources will be collected using JFR. When
   * set to {@code false} (default), metrics available from both sources will be collected using
   * JMX. Metrics available from only one source are unaffected by this setting.
   *
   * @param builder the runtime telemetry builder
   * @param preferJfrMetrics {@code true} to prefer JFR over JMX where both are available
   */
  public static void setPreferJfrMetrics(
      RuntimeTelemetryBuilder builder, boolean preferJfrMetrics) {
    if (setPreferJfrMetrics != null) {
      setPreferJfrMetrics.accept(builder, preferJfrMetrics);
    }
  }

  public static void internalSetPreferJfrMetrics(
      BiConsumer<RuntimeTelemetryBuilder, Boolean> setPreferJfrMetrics) {
    Experimental.setPreferJfrMetrics = setPreferJfrMetrics;
  }

  private Experimental() {}
}
