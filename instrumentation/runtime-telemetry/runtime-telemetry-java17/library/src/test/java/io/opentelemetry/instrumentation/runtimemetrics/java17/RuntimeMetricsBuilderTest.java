/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.JfrConfig;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import jdk.jfr.FlightRecorder;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // Testing deprecated API for backward compatibility
class RuntimeMetricsBuilderTest {

  @BeforeAll
  static void setup() {
    try {
      Class.forName("jdk.jfr.FlightRecorder");
    } catch (ClassNotFoundException exception) {
      Assumptions.abort("JFR not present");
    }
    Assumptions.assumeTrue(FlightRecorder.isAvailable(), "JFR not available");
  }

  @Test
  void defaultFeatures() {
    var defaultFeatures = new HashMap<JfrFeature, Boolean>();
    Arrays.stream(JfrFeature.values())
        .forEach(jfrFeature -> defaultFeatures.put(jfrFeature, jfrFeature.isDefaultEnabled()));

    assertThat(getEnabledFeatureMap(new RuntimeMetricsBuilder(OpenTelemetry.noop())))
        .isEqualTo(defaultFeatures);
  }

  @Test
  void enableAllFeatures() {
    assertThat(
            getEnabledFeatureMap(
                new RuntimeMetricsBuilder(OpenTelemetry.noop()).enableAllFeatures()))
        .allSatisfy((unused, enabled) -> assertThat(enabled).isTrue());
  }

  @Test
  void disableAllFeatures() {
    assertThat(
            getEnabledFeatureMap(
                new RuntimeMetricsBuilder(OpenTelemetry.noop()).disableAllFeatures()))
        .allSatisfy((unused, enabled) -> assertThat(enabled).isFalse());
  }

  @Test
  void enableDisableFeature() {
    var builder = new RuntimeMetricsBuilder(OpenTelemetry.noop());

    assertThat(getEnabledFeatureMap(builder).get(JfrFeature.BUFFER_METRICS)).isFalse();

    builder.enableFeature(JfrFeature.BUFFER_METRICS);
    assertThat(getEnabledFeatureMap(builder).get(JfrFeature.BUFFER_METRICS)).isTrue();
    builder.disableFeature(JfrFeature.BUFFER_METRICS);
    assertThat(getEnabledFeatureMap(builder).get(JfrFeature.BUFFER_METRICS)).isFalse();
  }

  @Test
  void build() {
    var openTelemetry = OpenTelemetry.noop();
    try (var jfrTelemetry = new RuntimeMetricsBuilder(openTelemetry).build()) {
      assertThat(getJfrRuntimeMetrics(jfrTelemetry).getRecordedEventHandlers())
          .hasSizeGreaterThan(0)
          .allSatisfy(handler -> assertThat(isDefaultEnabled(handler.getFeature())).isTrue());
    }
  }

  // Helper to access the unified module's state
  private static JfrConfig.JfrRuntimeMetrics getJfrRuntimeMetrics(RuntimeMetrics runtimeMetrics) {
    return (JfrConfig.JfrRuntimeMetrics) runtimeMetrics.getJfrRuntimeMetrics();
  }

  // Helper to access the unified module's state
  private static EnumMap<JfrFeature, Boolean> getEnabledFeatureMap(RuntimeMetricsBuilder builder) {
    JfrConfig jfrConfig = (JfrConfig) builder.getJfrConfig();
    EnumMap<JfrFeature, Boolean> result = new EnumMap<>(JfrFeature.class);
    for (JfrFeature feature : JfrFeature.values()) {
      result.put(
          feature,
          jfrConfig.enabledFeatureMap.get(
              io.opentelemetry.instrumentation.runtimetelemetry.internal.JfrFeature.valueOf(
                  feature.name())));
    }
    return result;
  }

  // Java17 legacy defaults: all non-overlapping features
  // plus CPU_COUNT_METRICS (which is emitted as cpu.count.limit)
  private static boolean isDefaultEnabled(
      io.opentelemetry.instrumentation.runtimetelemetry.internal.JfrFeature feature) {
    return !feature.overlapsWithJmx()
        || feature
            == io.opentelemetry.instrumentation.runtimetelemetry.internal.JfrFeature
                .CPU_COUNT_METRICS;
  }
}
