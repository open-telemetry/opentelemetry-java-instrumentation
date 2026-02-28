/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import javax.annotation.Nullable;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Experimental;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Internal;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.JfrConfig;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.JmxRuntimeMetricsFactory;
import java.util.List;

/** Builder for {@link RuntimeTelemetry}. */
public final class RuntimeTelemetryBuilder {

  private static final String DEFAULT_INSTRUMENTATION_NAME = "io.opentelemetry.runtime-telemetry";

  private final OpenTelemetry openTelemetry;
  private final JfrConfig jfrConfig;

  private boolean emitExperimentalMetrics;
  private boolean preferJfrMetrics;
  private boolean disableJmx;
  private boolean captureGcCause;
  // For backward compatibility: support separate instrumentation names for JMX and JFR metrics
  @Nullable private String jmxInstrumentationName;
  @Nullable private String jfrInstrumentationName;

  static {
    Experimental.internalSetEmitExperimentalMetrics(
        (builder, emit) -> {
          builder.emitExperimentalMetrics = emit;
          if (emit) {
            builder.jfrConfig.enableExperimentalFeatures();
          }
        });
    Experimental.internalSetPreferJfrMetrics(
        (builder, prefer) -> builder.preferJfrMetrics = prefer);
    Internal.internalSetEnableAllJfrFeatures(
        (builder, enable) -> {
          if (enable) {
            builder.jfrConfig.enableAllFeatures();
          }
        });
    Internal.internalSetDisableAllJfrFeatures(
        (builder, disable) -> {
          if (disable) {
            builder.jfrConfig.disableAllFeatures();
          }
        });
    Internal.internalSetEnableExperimentalJfrFeatures(
        (builder, enable) -> {
          if (enable) {
            builder.jfrConfig.enableExperimentalFeatures();
          }
        });
    Internal.internalSetCaptureGcCause((builder, capture) -> builder.captureGcCause = capture);
    Internal.internalSetUseLegacyJfrCpuCountMetric(
        (builder, useLegacy) -> builder.jfrConfig.setUseLegacyJfrCpuCountMetric(useLegacy));
    Internal.internalSetJmxInstrumentationName(
        (builder, name) -> builder.jmxInstrumentationName = name);
    Internal.internalSetJfrInstrumentationName(
        (builder, name) -> builder.jfrInstrumentationName = name);
    Internal.internalSetEnableJfrFeature(
        (builder, featureName) -> builder.jfrConfig.enableFeature(featureName));
    Internal.internalSetDisableJfrFeature(
        (builder, featureName) -> builder.jfrConfig.disableFeature(featureName));
    Internal.internalSetDisableJmx((builder, disable) -> builder.disableJmx = disable);
  }

  RuntimeTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
    this.jfrConfig = JfrConfig.create();
  }

  // Visible for testing
  JfrConfig getJfrConfig() {
    return jfrConfig;
  }

  /** Disable all JMX telemetry collection. Visible for testing. */
  @CanIgnoreReturnValue
  RuntimeTelemetryBuilder disableAllJmx() {
    disableJmx = true;
    return this;
  }

  /** Build and start a {@link RuntimeTelemetry} with the config from this builder. */
  public RuntimeTelemetry build() {
    // Use configured names, or fall back to default if not set
    String jmxName =
        jmxInstrumentationName != null ? jmxInstrumentationName : DEFAULT_INSTRUMENTATION_NAME;
    String jfrName =
        jfrInstrumentationName != null ? jfrInstrumentationName : DEFAULT_INSTRUMENTATION_NAME;

    Meter jmxMeter = getMeter(openTelemetry, jmxName);
    Meter jfrMeter = getMeter(openTelemetry, jfrName);

    List<AutoCloseable> observables =
        disableJmx
            ? emptyList()
            : JmxRuntimeMetricsFactory.buildObservables(
                emitExperimentalMetrics, captureGcCause, preferJfrMetrics, jmxMeter);
    AutoCloseable jfrTelemetry = jfrConfig.buildJfrTelemetry(preferJfrMetrics, jfrMeter);
    return new RuntimeTelemetry(observables, jfrTelemetry);
  }

  private static Meter getMeter(OpenTelemetry openTelemetry, String instrumentationName) {
    MeterBuilder meterBuilder = openTelemetry.meterBuilder(instrumentationName);
    String version = EmbeddedInstrumentationProperties.findVersion(instrumentationName);
    if (version != null) {
      meterBuilder.setInstrumentationVersion(version);
    }
    return meterBuilder.build();
  }
}
