/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Experimental;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Internal;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.JfrConfig;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.JmxRuntimeMetricsFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/** Builder for {@link RuntimeTelemetry}. */
public final class RuntimeTelemetryBuilder {

  private static final String DEFAULT_INSTRUMENTATION_NAME = "io.opentelemetry.runtime-telemetry";
  private static final List<String> EXPERIMENTAL_JFR_METRICS =
      asList(
          // the jvm.buffer.* metrics overlap with JMX, and are filtered back out of the JFR
          // selection when experimental JMX metrics are also enabled
          "jvm.buffer.count",
          "jvm.buffer.memory.limit",
          "jvm.buffer.memory.used",
          "jvm.cpu.context_switch",
          "jvm.cpu.longlock",
          "jvm.memory.allocation",
          "jvm.network.io",
          "jvm.network.time",
          "jvm.thread.virtual.pinned",
          "jvm.thread.virtual.submit_failed");

  private final OpenTelemetry openTelemetry;
  private final JfrConfig jfrConfig;

  private boolean emitExperimentalMetrics;
  private boolean emitExperimentalJfrMetrics;
  private boolean preferJfrMetrics;
  @Nullable private IncludeExclude jfrMetrics;
  private boolean suppressOverlappingJmxMetrics = true;
  private boolean disableJmx;
  private boolean captureGcCause;
  // For backward compatibility: support separate instrumentation names for JMX and JFR metrics
  @Nullable private String jmxInstrumentationName;
  @Nullable private String jfrInstrumentationName;

  static {
    Experimental.internalSetEmitExperimentalMetrics(
        (builder, emit) -> builder.emitExperimentalMetrics = emit);
    Experimental.internalSetEmitExperimentalJfrMetrics(
        (builder, emit) -> builder.emitExperimentalJfrMetrics = emit);
    Experimental.internalSetJfrMetrics((builder, selector) -> builder.jfrMetrics = selector);
    Experimental.internalSetPreferJfrMetrics(
        (builder, prefer) -> builder.preferJfrMetrics = prefer);
    Internal.internalSetJfrMetrics((builder, selector) -> builder.jfrMetrics = selector);
    Internal.internalSetSuppressOverlappingJmxMetrics(
        (builder, suppress) -> builder.suppressOverlappingJmxMetrics = suppress);
    Internal.internalSetCaptureGcCause((builder, capture) -> builder.captureGcCause = capture);
    Internal.internalSetUseLegacyJfrCpuCountMetric(
        (builder, useLegacy) -> builder.jfrConfig.setUseLegacyJfrCpuCountMetric(useLegacy));
    Internal.internalSetJmxInstrumentationName(
        (builder, name) -> builder.jmxInstrumentationName = name);
    Internal.internalSetJfrInstrumentationName(
        (builder, name) -> builder.jfrInstrumentationName = name);
    Internal.internalSetDisableJmx((builder, disable) -> builder.disableJmx = disable);
  }

  RuntimeTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
    this.jfrConfig = JfrConfig.create();
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

    IncludeExclude effectiveJfrMetrics = getEffectiveJfrMetrics();
    JfrConfig.JfrTelemetry jfrTelemetry =
        effectiveJfrMetrics == null
            ? new JfrConfig.JfrTelemetry(null, emptySet())
            : jfrConfig.buildJfrTelemetry(
                effectiveJfrMetrics::matches,
                getMeter(openTelemetry, jfrName),
                suppressOverlappingJmxMetrics && !disableJmx,
                emitExperimentalMetrics);
    Set<String> jfrMetricNames = jfrTelemetry.getMetricNames();

    Meter jmxMeter = getMeter(openTelemetry, jmxName);
    List<AutoCloseable> observables =
        disableJmx
            ? emptyList()
            : JmxRuntimeMetricsFactory.buildObservables(
                emitExperimentalMetrics,
                captureGcCause,
                metricName ->
                    !suppressOverlappingJmxMetrics || !jfrMetricNames.contains(metricName),
                jmxMeter);
    return new RuntimeTelemetry(observables, jfrTelemetry.getTelemetry());
  }

  @Nullable
  private IncludeExclude getEffectiveJfrMetrics() {
    IncludeExclude selector = jfrMetrics;

    // An exclude-only selector already selects every non-excluded metric.
    if (selector != null && !selector.isEmpty() && selector.getIncluded().isEmpty()) {
      return selector;
    }

    List<String> included = new ArrayList<>();
    List<String> excluded = emptyList();
    if (selector != null) {
      included.addAll(selector.getIncluded());
      excluded = selector.getExcluded();
    }
    if (preferJfrMetrics) {
      included.addAll(Experimental.JMX_OVERLAPPING_JFR_METRICS);
    }
    if (emitExperimentalJfrMetrics) {
      included.addAll(EXPERIMENTAL_JFR_METRICS);
    }

    if (included.isEmpty()) {
      return null;
    }

    return IncludeExclude.builder().setIncluded(included).setExcluded(excluded).build();
  }

  private static Meter getMeter(OpenTelemetry openTelemetry, String instrumentationName) {
    MeterBuilder meterBuilder = openTelemetry.meterBuilder(instrumentationName);
    // version file is generated from the gradle module name; the emitted scope may be a legacy
    // name from a previously-renamed module (e.g. runtime-telemetry-java8) that has no version
    // file of its own, so always look the version up under the current module name
    String version = EmbeddedInstrumentationProperties.findVersion(DEFAULT_INSTRUMENTATION_NAME);
    if (version != null) {
      meterBuilder.setInstrumentationVersion(version);
    }
    return meterBuilder.build();
  }
}
