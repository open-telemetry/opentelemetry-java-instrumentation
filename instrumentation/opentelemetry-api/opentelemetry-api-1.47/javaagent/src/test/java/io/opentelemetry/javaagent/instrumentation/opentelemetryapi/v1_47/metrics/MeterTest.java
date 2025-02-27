/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_47.metrics;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.incubator.metrics.ExtendedDoubleCounter;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleGauge;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogram;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleUpDownCounter;
import io.opentelemetry.api.incubator.metrics.ExtendedLongCounter;
import io.opentelemetry.api.incubator.metrics.ExtendedLongGauge;
import io.opentelemetry.api.incubator.metrics.ExtendedLongHistogram;
import io.opentelemetry.api.incubator.metrics.ExtendedLongUpDownCounter;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.DoubleUpDownCounter;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MeterTest {

  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void isEnabled() {
    Meter disabledMeter = testing.getOpenTelemetry().getMeter("disabled-meter");
    Meter enabledMeter = testing.getOpenTelemetry().getMeter("enabled-meter");
    testEnabled(disabledMeter, false);
    testEnabled(enabledMeter, true);
  }

  private static void testEnabled(Meter meter, boolean expected) {
    LongCounterBuilder counterBuilder = meter.counterBuilder("test");
    LongCounter longCounter = counterBuilder.build();
    assertThat(longCounter).isInstanceOf(ExtendedLongCounter.class);
    assertThat(((ExtendedLongCounter) longCounter).isEnabled()).isEqualTo(expected);

    DoubleCounter doubleCounter = counterBuilder.ofDoubles().build();
    assertThat(doubleCounter).isInstanceOf(ExtendedDoubleCounter.class);
    assertThat(((ExtendedDoubleCounter) doubleCounter).isEnabled()).isEqualTo(expected);

    LongUpDownCounterBuilder upDownCounterBuilder = meter.upDownCounterBuilder("test");
    LongUpDownCounter longUpDownCounter = upDownCounterBuilder.build();
    assertThat(longUpDownCounter).isInstanceOf(ExtendedLongUpDownCounter.class);
    assertThat(((ExtendedLongUpDownCounter) longUpDownCounter).isEnabled()).isEqualTo(expected);

    DoubleUpDownCounter doubleUpDownCounter = upDownCounterBuilder.ofDoubles().build();
    assertThat(doubleUpDownCounter).isInstanceOf(ExtendedDoubleUpDownCounter.class);
    assertThat(((ExtendedDoubleUpDownCounter) doubleUpDownCounter).isEnabled()).isEqualTo(expected);

    DoubleGaugeBuilder gaugeBuilder = meter.gaugeBuilder("test");
    DoubleGauge doubleGauge = gaugeBuilder.build();
    assertThat(doubleGauge).isInstanceOf(ExtendedDoubleGauge.class);
    assertThat(((ExtendedDoubleGauge) doubleGauge).isEnabled()).isEqualTo(expected);

    LongGauge longGauge = gaugeBuilder.ofLongs().build();
    assertThat(longGauge).isInstanceOf(ExtendedLongGauge.class);
    assertThat(((ExtendedLongGauge) longGauge).isEnabled()).isEqualTo(expected);

    DoubleHistogramBuilder histogramBuilder = meter.histogramBuilder("test");
    DoubleHistogram doubleHistogram = histogramBuilder.build();
    assertThat(doubleHistogram).isInstanceOf(ExtendedDoubleHistogram.class);
    assertThat(((ExtendedDoubleHistogram) doubleHistogram).isEnabled()).isEqualTo(expected);

    LongHistogram longHistogram = histogramBuilder.ofLongs().build();
    assertThat(longHistogram).isInstanceOf(ExtendedLongHistogram.class);
    assertThat(((ExtendedLongHistogram) longHistogram).isEnabled()).isEqualTo(expected);
  }
}
