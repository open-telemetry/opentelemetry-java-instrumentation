/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MeterFilter;
import io.opentelemetry.instrumentation.micrometer.v1_5.internal.Experimental;
import io.opentelemetry.instrumentation.micrometer.v1_5.internal.OpenTelemetryInstrument;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SuppressionTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void suppressionCreatesMarkedMetersWithoutExportsOrCompanions(boolean prometheusMode) {
    InMemoryMetricReader reader = InMemoryMetricReader.create();
    AtomicInteger callbacks = new AtomicInteger();
    try (SdkMeterProvider provider =
        SdkMeterProvider.builder().registerMetricReader(reader).build()) {
      OpenTelemetryMeterRegistryBuilder builder =
          OpenTelemetryMeterRegistry.builder(
                  OpenTelemetrySdk.builder().setMeterProvider(provider).build())
              .setPrometheusMode(prometheusMode);
      Experimental.setSuppressionPredicate(builder, id -> id.getName().startsWith("suppressed."));
      Experimental.setMicrometerHistogramGaugesEnabled(builder, true);
      MeterRegistry registry = builder.build();
      try {
        registry.counter("suppressed.counter").increment(7);
        Gauge.builder("suppressed.gauge", callbacks, AtomicInteger::incrementAndGet)
            .register(registry);
        Timer.builder("suppressed.timer")
            .publishPercentiles(0.5)
            .publishPercentileHistogram()
            .register(registry)
            .record(Duration.ofSeconds(7));
        DistributionSummary.builder("suppressed.summary")
            .publishPercentiles(0.5)
            .publishPercentileHistogram()
            .register(registry)
            .record(7);
        LongTaskTimer.builder("suppressed.longtask").register(registry).start();
        FunctionTimer.builder(
                "suppressed.functiontimer",
                callbacks,
                AtomicInteger::incrementAndGet,
                AtomicInteger::incrementAndGet,
                SECONDS)
            .register(registry);
        FunctionCounter.builder(
                "suppressed.functioncounter", callbacks, AtomicInteger::incrementAndGet)
            .register(registry);
        Meter.builder(
                "suppressed.meter",
                Meter.Type.OTHER,
                singletonList(
                    new Measurement(() -> (double) callbacks.incrementAndGet(), Statistic.VALUE)))
            .register(registry);

        assertThat(registry.getMeters())
            .hasSize(8)
            .allSatisfy(meter -> assertThat(meter).isInstanceOf(OpenTelemetryInstrument.class));
        assertThat(reader.collectAllMetrics()).isEmpty();
        assertThat(callbacks).hasValue(0);

        // A surviving observation proves collection works and the predicate is selective.
        registry.counter("retained").increment(3);
        assertThat(reader.collectAllMetrics())
            .extracting(MetricData::getName)
            .containsExactly("retained");
        assertThat(registry.getMeters()).hasSize(9);
        assertThat(callbacks).hasValue(0);
      } finally {
        registry.close();
      }
    }
  }

  @Test
  void predicateReceivesFilteredIdBeforeExportNaming() {
    InMemoryMetricReader reader = InMemoryMetricReader.create();
    try (SdkMeterProvider provider =
        SdkMeterProvider.builder().registerMetricReader(reader).build()) {
      OpenTelemetryMeterRegistryBuilder builder =
          OpenTelemetryMeterRegistry.builder(
              OpenTelemetrySdk.builder().setMeterProvider(provider).build());
      Experimental.setSuppressionPredicate(
          builder,
          id -> {
            assertThat(id.getName()).startsWith("mapped.");
            assertThat(id.getTag("source")).isEqualTo("filter");
            return id.getName().equals("mapped.drop");
          });
      MeterRegistry registry = builder.build();
      try {
        registry
            .config()
            .meterFilter(
                new MeterFilter() {
                  @Override
                  public Meter.Id map(Meter.Id id) {
                    return id.withName("mapped." + id.getName())
                        .withTag(Tag.of("source", "filter"));
                  }
                });
        registry.config().namingConvention((name, type, unit) -> name.replace('.', '_'));
        Counter dropped = registry.counter("drop");
        dropped.increment(7);
        registry.counter("keep").increment(3);
        assertThat(dropped).isInstanceOf(OpenTelemetryInstrument.class);
        assertThat(reader.collectAllMetrics())
            .singleElement()
            .satisfies(
                metric -> {
                  assertThat(metric.getName()).isEqualTo("mapped_keep");
                  assertThat(metric.getDoubleSumData().getPoints())
                      .singleElement()
                      .satisfies(point -> assertThat(point.getValue()).isEqualTo(3));
                });
      } finally {
        registry.close();
      }
    }
  }

  @Test
  void suppressedMetersCanBeRemovedAndRegisteredAgain() {
    InMemoryMetricReader reader = InMemoryMetricReader.create();
    AtomicInteger callbacks = new AtomicInteger();
    try (SdkMeterProvider provider =
        SdkMeterProvider.builder().registerMetricReader(reader).build()) {
      OpenTelemetryMeterRegistryBuilder builder =
          OpenTelemetryMeterRegistry.builder(
              OpenTelemetrySdk.builder().setMeterProvider(provider).build());
      Experimental.setSuppressionPredicate(builder, id -> !id.getName().equals("retained"));
      Experimental.setMicrometerHistogramGaugesEnabled(builder, true);
      MeterRegistry registry = builder.build();
      try {
        Gauge firstGauge =
            Gauge.builder("gauge", callbacks, AtomicInteger::incrementAndGet).register(registry);
        Timer firstTimer = Timer.builder("timer").publishPercentiles(0.5).register(registry);
        firstTimer.record(Duration.ofSeconds(7));
        assertThat(registry.remove(firstGauge)).isSameAs(firstGauge);
        assertThat(registry.remove(firstTimer)).isSameAs(firstTimer);
        assertThat(registry.getMeters()).isEmpty();
        Gauge secondGauge =
            Gauge.builder("gauge", callbacks, AtomicInteger::incrementAndGet).register(registry);
        Timer secondTimer = Timer.builder("timer").publishPercentiles(0.5).register(registry);
        secondTimer.record(Duration.ofSeconds(9));
        assertThat(secondGauge).isNotSameAs(firstGauge).isInstanceOf(OpenTelemetryInstrument.class);
        assertThat(secondTimer).isNotSameAs(firstTimer).isInstanceOf(OpenTelemetryInstrument.class);
        registry.counter("retained").increment(3);
        assertThat(registry.getMeters()).hasSize(3);
        assertThat(reader.collectAllMetrics())
            .extracting(MetricData::getName)
            .containsExactly("retained");
        assertThat(callbacks).hasValue(0);
      } finally {
        registry.close();
      }
    }
  }
}
