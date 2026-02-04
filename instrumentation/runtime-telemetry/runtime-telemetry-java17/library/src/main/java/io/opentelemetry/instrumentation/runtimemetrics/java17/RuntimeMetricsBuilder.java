/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.runtimetelemetry.RuntimeTelemetry;
import io.opentelemetry.instrumentation.runtimetelemetry.RuntimeTelemetryBuilder;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Experimental;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Internal;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Builder for {@link RuntimeMetrics}.
 *
 * @deprecated Use {@link RuntimeTelemetryBuilder} in the {@code runtime-telemetry} module instead.
 */
@Deprecated
public final class RuntimeMetricsBuilder {

  private final RuntimeTelemetryBuilder delegate;

  RuntimeMetricsBuilder(OpenTelemetry openTelemetry) {
    this.delegate = RuntimeTelemetry.builder(openTelemetry);
    // Set instrumentation name for backward compatibility
    // JMX metrics use java8 scope (matching old behavior where java8 module handled JMX)
    Internal.setJmxInstrumentationName(delegate, "io.opentelemetry.runtime-telemetry-java8");
    Internal.setJfrInstrumentationName(delegate, "io.opentelemetry.runtime-telemetry-java17");
    // Use legacy metric for backward compatibility (jvm.cpu.limit instead of jvm.cpu.count)
    Internal.setUseLegacyJfrCpuCountMetric(delegate, true);
    // java17 default for captureGcCause was false (unified module default is true)
    Internal.setCaptureGcCause(delegate, false);

    // Disable all JFR features first, then selectively enable based on java17 defaults.
    // This ensures backward compatibility even if the unified module adds new default-enabled
    // features in the future.
    Internal.setDisableAllJfrFeatures(delegate, true);

    // Enable features with java17 defaults
    for (JfrFeature feature : JfrFeature.values()) {
      if (feature.isDefaultEnabled()) {
        Internal.setEnableJfrFeature(delegate, feature.name());
      }
    }
  }

  // Only used by tests
  Object getJfrConfig() {
    try {
      Method method = RuntimeTelemetryBuilder.class.getDeclaredMethod("getJfrConfig");
      method.setAccessible(true);
      return method.invoke(delegate);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to access JfrConfig via reflection", e);
    }
  }

  /**
   * Enable telemetry collection for all {@link JfrFeature}s.
   *
   * @deprecated Use {@link RuntimeTelemetry#builder(OpenTelemetry)} in the {@code
   *     runtime-telemetry} module instead. To enable experimental features, use {@link
   *     Experimental#setEmitExperimentalMetrics(RuntimeTelemetryBuilder, boolean)}. To enable JFR
   *     collection for metrics that overlap with JMX, use {@link
   *     Experimental#setPreferJfrMetrics(RuntimeTelemetryBuilder, boolean)}.
   */
  @Deprecated
  @CanIgnoreReturnValue
  public RuntimeMetricsBuilder enableAllFeatures() {
    Arrays.stream(JfrFeature.values()).forEach(this::enableFeature);
    return this;
  }

  /**
   * Enable telemetry collection associated with the {@link JfrFeature}.
   *
   * @deprecated Use {@link RuntimeTelemetry#builder(OpenTelemetry)} in the {@code
   *     runtime-telemetry} module instead. To enable experimental features, use {@link
   *     Experimental#setEmitExperimentalMetrics(RuntimeTelemetryBuilder, boolean)}. To enable JFR
   *     collection for metrics that overlap with JMX, use {@link
   *     Experimental#setPreferJfrMetrics(RuntimeTelemetryBuilder, boolean)}. To disable specific
   *     metrics, configure metric views.
   */
  @Deprecated
  @CanIgnoreReturnValue
  public RuntimeMetricsBuilder enableFeature(JfrFeature feature) {
    Internal.setEnableJfrFeature(delegate, feature.name());
    return this;
  }

  /**
   * Disable telemetry collection for all {@link JfrFeature}s.
   *
   * @deprecated Use {@link RuntimeTelemetry#builder(OpenTelemetry)} in the {@code
   *     runtime-telemetry} module instead. To disable specific metrics, configure metric views.
   */
  @Deprecated
  @CanIgnoreReturnValue
  public RuntimeMetricsBuilder disableAllFeatures() {
    Arrays.stream(JfrFeature.values()).forEach(this::disableFeature);
    return this;
  }

  /**
   * Disable telemetry collection for all metrics.
   *
   * @deprecated Use {@link RuntimeTelemetry#builder(OpenTelemetry)} in the {@code
   *     runtime-telemetry} module instead. To disable specific metrics, configure metric views.
   */
  @Deprecated
  @CanIgnoreReturnValue
  public RuntimeMetricsBuilder disableAllMetrics() {
    disableAllFeatures();
    disableAllJmx();
    return this;
  }

  /**
   * Disable telemetry collection associated with the {@link JfrFeature}.
   *
   * @deprecated Use {@link RuntimeTelemetry#builder(OpenTelemetry)} in the {@code
   *     runtime-telemetry} module instead. To disable specific metrics, configure metric views.
   */
  @Deprecated
  @CanIgnoreReturnValue
  public RuntimeMetricsBuilder disableFeature(JfrFeature feature) {
    Internal.setDisableJfrFeature(delegate, feature.name());
    return this;
  }

  /**
   * Disable all JMX telemetry collection.
   *
   * @deprecated Use {@link RuntimeTelemetry#builder(OpenTelemetry)} in the {@code
   *     runtime-telemetry} module instead. To disable specific metrics, configure metric views.
   */
  @Deprecated
  @CanIgnoreReturnValue
  public RuntimeMetricsBuilder disableAllJmx() {
    Internal.setDisableJmx(delegate, true);
    return this;
  }

  /**
   * Enable experimental JMX telemetry collection.
   *
   * @deprecated Use {@link Experimental#setEmitExperimentalMetrics(RuntimeTelemetryBuilder,
   *     boolean)} instead.
   */
  @Deprecated
  @CanIgnoreReturnValue
  public RuntimeMetricsBuilder emitExperimentalTelemetry() {
    Experimental.setEmitExperimentalMetrics(delegate, true);
    return this;
  }

  /**
   * Enable the capture of the jvm.gc.cause attribute with the jvm.gc.duration metric.
   *
   * @deprecated Use {@link RuntimeTelemetry#builder(OpenTelemetry)} in the {@code
   *     runtime-telemetry} module instead. The unified {@link RuntimeTelemetryBuilder} enables GC
   *     cause capture by default.
   */
  @Deprecated
  @CanIgnoreReturnValue
  public RuntimeMetricsBuilder captureGcCause() {
    Internal.setCaptureGcCause(delegate, true);
    return this;
  }

  /**
   * Build and start an {@link RuntimeMetrics} with the config from this builder.
   *
   * @deprecated Use {@link RuntimeTelemetry#builder(OpenTelemetry)} in the {@code
   *     runtime-telemetry} module instead.
   */
  @Deprecated
  public RuntimeMetrics build() {
    return new RuntimeMetrics(delegate.build());
  }
}
