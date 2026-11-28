/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CompositeTimeGaugeTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void readsFallbackAndExportsObservedValue() {
    assertThat(Metrics.globalRegistry.getRegistries()).hasSize(1);
    MeterRegistry agentRegistry = Metrics.globalRegistry.getRegistries().iterator().next();
    CompositeMeterRegistry composite = new CompositeMeterRegistry();
    composite.add(agentRegistry);
    AtomicLong value = new AtomicLong(42);
    TimeGauge gauge =
        TimeGauge.builder("testCompositeTimeGauge", value, MILLISECONDS, AtomicLong::doubleValue)
            .register(composite);
    try {
      assertThat(gauge.getClass().getName()).contains("CompositeTimeGauge");
      assertThat(gauge.value()).isEqualTo(0);

      testing.waitAndAssertMetrics(
          "io.opentelemetry.micrometer-1.5",
          metric ->
              metric
                  .hasName("testCompositeTimeGauge")
                  .hasUnit("seconds")
                  .hasDoubleGaugeSatisfying(
                      data -> data.hasPointsSatisfying(point -> point.hasValue(0.042))));
    } finally {
      composite.remove(gauge);
      composite.remove(agentRegistry);
      composite.close();
    }
  }
}
