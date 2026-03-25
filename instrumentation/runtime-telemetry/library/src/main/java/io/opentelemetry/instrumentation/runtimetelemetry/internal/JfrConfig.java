/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.metrics.Meter;
import javax.annotation.Nullable;

/**
 * Configuration holder for JFR telemetry. On Java 8, this is a no-op implementation since JFR is
 * not available. On Java 17+, this is replaced by an implementation that manages JFR features.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class JfrConfig {

  public static JfrConfig create() {
    return new JfrConfig();
  }

  @CanIgnoreReturnValue
  public JfrConfig enableAllFeatures() {
    return this;
  }

  @CanIgnoreReturnValue
  public JfrConfig disableAllFeatures() {
    return this;
  }

  @CanIgnoreReturnValue
  public JfrConfig enableExperimentalFeatures() {
    return this;
  }

  @CanIgnoreReturnValue
  public JfrConfig enableFeature(String featureName) {
    return this;
  }

  @CanIgnoreReturnValue
  public JfrConfig disableFeature(String featureName) {
    return this;
  }

  @CanIgnoreReturnValue
  public JfrConfig setUseLegacyJfrCpuCountMetric(boolean useLegacy) {
    return this;
  }

  @Nullable
  public AutoCloseable buildJfrTelemetry(boolean preferJfrMetrics, Meter meter) {
    return null;
  }

  private JfrConfig() {}
}
