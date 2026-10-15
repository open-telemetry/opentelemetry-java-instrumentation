/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.micrometer.v1_5.internal.Internal;
import org.junit.jupiter.api.Test;

class HiddenMetersTest {

  @Test
  void metersAreVisibleByDefault() {
    MeterRegistry registry = OpenTelemetryMeterRegistry.create(OpenTelemetry.noop());
    registry.counter("test.counter").increment();

    assertThat(registry.getMeters()).hasSize(1);
    assertThat(registry.find("test.counter").counter()).isNotNull();
  }

  @Test
  void metersAreHiddenWhenConfigured() {
    OpenTelemetryMeterRegistryBuilder builder =
        OpenTelemetryMeterRegistry.builder(OpenTelemetry.noop());
    Internal.setMetersHiddenFromSearch(builder, true);
    MeterRegistry registry = builder.build();

    registry.counter("test.counter").increment();

    assertThat(registry.getMeters()).isEmpty();
    assertThat(registry.find("test.counter").counter()).isNull();
    assertThatExceptionOfType(MeterNotFoundException.class)
        .isThrownBy(() -> registry.get("test.counter").counter());
  }

  @Test
  void hiddenMetersAreStillRegisteredAndRemovable() {
    OpenTelemetryMeterRegistryBuilder builder =
        OpenTelemetryMeterRegistry.builder(OpenTelemetry.noop());
    Internal.setMetersHiddenFromSearch(builder, true);
    MeterRegistry registry = builder.build();

    Counter counter = registry.counter("test.counter");
    // registration is deduplicated even though the meter is hidden from the search apis
    assertThat(registry.counter("test.counter")).isSameAs(counter);

    registry.forEachMeter(registry::remove);
    assertThat(registry.counter("test.counter")).isNotSameAs(counter);
  }
}
