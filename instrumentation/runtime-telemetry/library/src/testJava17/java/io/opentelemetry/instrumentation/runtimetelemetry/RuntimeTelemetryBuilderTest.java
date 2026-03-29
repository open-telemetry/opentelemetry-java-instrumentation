/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.JfrConfig;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.JfrFeature;
import java.util.Arrays;
import java.util.HashMap;
import jdk.jfr.FlightRecorder;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RuntimeTelemetryBuilderTest {

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
    // By default, features that don't overlap with JMX AND are not experimental are enabled
    var defaultFeatures = new HashMap<JfrFeature, Boolean>();
    Arrays.stream(JfrFeature.values())
        .forEach(
            jfrFeature ->
                defaultFeatures.put(
                    jfrFeature, !jfrFeature.overlapsWithJmx() && !jfrFeature.isExperimental()));

    assertThat(newBuilder().getJfrConfig().enabledFeatureMap).isEqualTo(defaultFeatures);
  }

  @Test
  void enableAllFeatures() {
    assertThat(newBuilder().getJfrConfig().enableAllFeatures().enabledFeatureMap)
        .allSatisfy((unused, enabled) -> assertThat(enabled).isTrue());
  }

  @Test
  void disableAllFeatures() {
    assertThat(newBuilder().getJfrConfig().disableAllFeatures().enabledFeatureMap)
        .allSatisfy((unused, enabled) -> assertThat(enabled).isFalse());
  }

  @Test
  void enableDisableFeature() {
    var builder = RuntimeTelemetry.builder(OpenTelemetry.noop());

    // BUFFER_METRICS overlaps with JMX and is experimental, so it's disabled by default
    assertThat(builder.getJfrConfig().enabledFeatureMap.get(JfrFeature.BUFFER_METRICS)).isFalse();

    builder.getJfrConfig().enableFeature(JfrFeature.BUFFER_METRICS);
    assertThat(builder.getJfrConfig().enabledFeatureMap.get(JfrFeature.BUFFER_METRICS)).isTrue();
    builder.getJfrConfig().disableFeature(JfrFeature.BUFFER_METRICS);
    assertThat(builder.getJfrConfig().enabledFeatureMap.get(JfrFeature.BUFFER_METRICS)).isFalse();
  }

  @Test
  void build_DefaultNoJfr() {
    // By default, no JFR features are enabled because all features either overlap
    // with JMX or are experimental
    var openTelemetry = OpenTelemetry.noop();
    try (var runtimeTelemetry = RuntimeTelemetry.builder(openTelemetry).build()) {
      assertThat(runtimeTelemetry.getJfrTelemetry()).isNull();
    }
  }

  @Test
  void build_WithFeatureEnabled() {
    var openTelemetry = OpenTelemetry.noop();
    var builder = RuntimeTelemetry.builder(openTelemetry);
    builder.getJfrConfig().enableFeature(JfrFeature.LOCK_METRICS);
    try (var runtimeTelemetry = builder.build()) {
      var jfrRuntimeMetrics = (JfrConfig.JfrRuntimeMetrics) runtimeTelemetry.getJfrTelemetry();
      assertThat(jfrRuntimeMetrics).isNotNull();
      assertThat(jfrRuntimeMetrics.getRecordedEventHandlers())
          .hasSizeGreaterThan(0)
          .allSatisfy(
              handler -> assertThat(handler.getFeature()).isEqualTo(JfrFeature.LOCK_METRICS));
    }
  }

  private static RuntimeTelemetryBuilder newBuilder() {
    return RuntimeTelemetry.builder(OpenTelemetry.noop());
  }
}
