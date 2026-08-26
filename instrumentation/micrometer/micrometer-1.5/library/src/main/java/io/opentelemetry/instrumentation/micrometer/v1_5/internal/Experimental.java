/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5.internal;

import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistryBuilder;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

/**
 * This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 */
public final class Experimental {

  @Nullable
  private static volatile BiConsumer<OpenTelemetryMeterRegistryBuilder, Boolean>
      setMicrometerHistogramGaugesEnabled;

  /**
   * Enables the generation of gauge-based Micrometer histograms. While the Micrometer bridge is
   * able to map Micrometer's {@code DistributionSummary} and {@code Timer} service level objectives
   * to OpenTelemetry histogram buckets, it might not cover all cases that are normally supported by
   * Micrometer (e.g. the bridge is not able to translate percentiles). With this setting enabled,
   * the Micrometer bridge will additionally emit Micrometer service level objectives and
   * percentiles as separate gauges.
   *
   * <p>Note that this setting does not concern the {@code LongTaskTimer}, as it is not bridged to
   * an OpenTelemetry histogram.
   *
   * <p>This is disabled by default, set this to {@code true} to enable gauge-based Micrometer
   * histograms.
   */
  public static void setMicrometerHistogramGaugesEnabled(
      OpenTelemetryMeterRegistryBuilder builder, boolean enabled) {
    if (setMicrometerHistogramGaugesEnabled != null) {
      setMicrometerHistogramGaugesEnabled.accept(builder, enabled);
    }
  }

  public static void internalSetMicrometerHistogramGaugesEnabled(
      BiConsumer<OpenTelemetryMeterRegistryBuilder, Boolean> setMicrometerHistogramGaugesEnabled) {
    Experimental.setMicrometerHistogramGaugesEnabled = setMicrometerHistogramGaugesEnabled;
  }

  private Experimental() {}
}
