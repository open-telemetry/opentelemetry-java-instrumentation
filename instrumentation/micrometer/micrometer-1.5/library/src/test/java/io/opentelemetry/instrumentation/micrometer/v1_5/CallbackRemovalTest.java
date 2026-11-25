/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CallbackRemovalTest {

  @Test
  void removedGaugeReleasesClosedHandle() {
    MeterRegistry registry = OpenTelemetryMeterRegistry.create(OpenTelemetry.noop());
    Gauge gauge = Gauge.builder("gauge", new AtomicLong(), AtomicLong::get).register(registry);

    registry.remove(gauge);

    assertThat(gauge).extracting("observableGauge").isNull();
    ((RemovableMeter) gauge).onRemove();
  }

  @Test
  void removedFunctionCounterReleasesClosedHandle() {
    MeterRegistry registry = OpenTelemetryMeterRegistry.create(OpenTelemetry.noop());
    FunctionCounter counter =
        FunctionCounter.builder("counter", new AtomicLong(), AtomicLong::get).register(registry);

    registry.remove(counter);

    assertThat(counter).extracting("observableCount").isNull();
    ((RemovableMeter) counter).onRemove();
  }

  @Test
  void removedFunctionTimerReleasesClosedHandles() {
    MeterRegistry registry = OpenTelemetryMeterRegistry.create(OpenTelemetry.noop());
    FunctionTimer timer =
        FunctionTimer.builder(
                "timer", new AtomicLong(), AtomicLong::get, AtomicLong::get, TimeUnit.SECONDS)
            .register(registry);

    registry.remove(timer);

    assertThat(timer).extracting("observableCount", "observableTotalTime").containsOnlyNulls();
    ((RemovableMeter) timer).onRemove();
  }
}
