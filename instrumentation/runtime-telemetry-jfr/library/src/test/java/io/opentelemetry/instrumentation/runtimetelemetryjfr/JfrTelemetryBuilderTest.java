/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetryjfr;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import java.util.Arrays;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

class JfrTelemetryBuilderTest {

  @Test
  void defaultFeatures() {
    var defaultFeatures = new HashMap<JfrFeature, Boolean>();
    Arrays.stream(JfrFeature.values())
        .forEach(jfrFeature -> defaultFeatures.put(jfrFeature, jfrFeature.isDefaultEnabled()));

    assertThat(new JfrTelemetryBuilder(OpenTelemetry.noop()).enabledFeatureMap)
        .isEqualTo(defaultFeatures);
  }

  @Test
  void enableAllFeatures() {
    assertThat(new JfrTelemetryBuilder(OpenTelemetry.noop()).enableAllFeatures().enabledFeatureMap)
        .allSatisfy((unused, enabled) -> assertThat(enabled).isTrue());
  }

  @Test
  void disableAllFeatures() {
    assertThat(new JfrTelemetryBuilder(OpenTelemetry.noop()).disableAllFeatures().enabledFeatureMap)
        .allSatisfy((unused, enabled) -> assertThat(enabled).isFalse());
  }

  @Test
  void enableDisableFeature() {
    var builder = new JfrTelemetryBuilder(OpenTelemetry.noop());

    assertThat(builder.enabledFeatureMap.get(JfrFeature.BUFFER_METRICS)).isFalse();

    builder.enableFeature(JfrFeature.BUFFER_METRICS);
    assertThat(builder.enabledFeatureMap.get(JfrFeature.BUFFER_METRICS)).isTrue();
    builder.disableFeature(JfrFeature.BUFFER_METRICS);
    assertThat(builder.enabledFeatureMap.get(JfrFeature.BUFFER_METRICS)).isFalse();
  }

  @Test
  void build() {
    var openTelemetry = OpenTelemetry.noop();
    try (var jfrTelemetry = new JfrTelemetryBuilder(openTelemetry).build()) {
      assertThat(jfrTelemetry.getOpenTelemetry()).isSameAs(openTelemetry);
      assertThat(jfrTelemetry.getRecordedEventHandlers())
          .hasSizeGreaterThan(0)
          .allSatisfy(handler -> assertThat(handler.getFeature().isDefaultEnabled()).isTrue());
    }
  }
}
