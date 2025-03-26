/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import java.util.Arrays;
import java.util.HashMap;
import jdk.jfr.FlightRecorder;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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

    assertThat(new RuntimeMetricsBuilder(OpenTelemetry.noop()).enabledFeatureMap)
        .isEqualTo(defaultFeatures);
  }

  @Test
  void enableAllFeatures() {
    assertThat(
            new RuntimeMetricsBuilder(OpenTelemetry.noop()).enableAllFeatures().enabledFeatureMap)
        .allSatisfy((unused, enabled) -> assertThat(enabled).isTrue());
  }

  @Test
  void disableAllFeatures() {
    assertThat(
            new RuntimeMetricsBuilder(OpenTelemetry.noop()).disableAllFeatures().enabledFeatureMap)
        .allSatisfy((unused, enabled) -> assertThat(enabled).isFalse());
  }

  @Test
  void enableDisableFeature() {
    var builder = new RuntimeMetricsBuilder(OpenTelemetry.noop());

    assertThat(builder.enabledFeatureMap.get(JfrFeature.BUFFER_METRICS)).isFalse();

    builder.enableFeature(JfrFeature.BUFFER_METRICS);
    assertThat(builder.enabledFeatureMap.get(JfrFeature.BUFFER_METRICS)).isTrue();
    builder.disableFeature(JfrFeature.BUFFER_METRICS);
    assertThat(builder.enabledFeatureMap.get(JfrFeature.BUFFER_METRICS)).isFalse();
  }

  @Test
  void build() {
    var openTelemetry = OpenTelemetry.noop();
    try (var jfrTelemetry = new RuntimeMetricsBuilder(openTelemetry).build()) {
      assertThat(jfrTelemetry.getOpenTelemetry()).isSameAs(openTelemetry);
      assertThat(jfrTelemetry.getJfrRuntimeMetrics().getRecordedEventHandlers())
          .hasSizeGreaterThan(0)
          .allSatisfy(handler -> assertThat(handler.getFeature().isDefaultEnabled()).isTrue());
    }
  }
}
