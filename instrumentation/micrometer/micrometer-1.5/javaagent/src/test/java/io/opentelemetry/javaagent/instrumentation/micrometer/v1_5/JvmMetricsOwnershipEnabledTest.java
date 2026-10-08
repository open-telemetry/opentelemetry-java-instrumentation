/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.time.Duration;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

// runs with jvm-metrics-ownership enabled and jvm.classes.unloaded kept, see build.gradle.kts
class JvmMetricsOwnershipEnabledTest {
  private static final String MICROMETER_SCOPE = "io.opentelemetry.micrometer-1.5";
  private static final String RUNTIME_TELEMETRY_SCOPE = "io.opentelemetry.runtime-telemetry-java8";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void classLoaderMetricsAreReportedByRuntimeTelemetry() {
    new ClassLoaderMetrics().bindTo(Metrics.globalRegistry);

    testing.waitAndAssertMetrics(
        RUNTIME_TELEMETRY_SCOPE, "jvm.class.count", AbstractIterableAssert::isNotEmpty);
    // kept, so still bridged
    testing.waitAndAssertMetrics(
        MICROMETER_SCOPE, "jvm.classes.unloaded", AbstractIterableAssert::isNotEmpty);

    assertThat(testing.metrics())
        .filteredOn(
            metric -> metric.getInstrumentationScopeInfo().getName().equals(MICROMETER_SCOPE))
        .extracting(MetricData::getName)
        .doesNotContain("jvm.classes.loaded", "jvm.classes.loaded.count");
  }

  @Test
  void memoryThreadsAndCpuUseNativeObservationsWhileExtrasRemain() {
    new JvmMemoryMetrics().bindTo(Metrics.globalRegistry);
    new JvmThreadMetrics().bindTo(Metrics.globalRegistry);
    new ProcessorMetrics().bindTo(Metrics.globalRegistry);
    for (String name :
        new String[] {
          "jvm.memory.used",
          "jvm.memory.committed",
          "jvm.memory.limit",
          "jvm.thread.count",
          "jvm.cpu.count"
        }) {
      testing.waitAndAssertMetrics(
          RUNTIME_TELEMETRY_SCOPE, name, AbstractIterableAssert::isNotEmpty);
    }
    // Buffers are retained because experimental JMX telemetry is disabled in this task.
    for (String name : new String[] {"jvm.buffer.count", "jvm.threads.peak"}) {
      testing.waitAndAssertMetrics(MICROMETER_SCOPE, name, AbstractIterableAssert::isNotEmpty);
    }
    assertThat(testing.metrics())
        .filteredOn(
            metric -> metric.getInstrumentationScopeInfo().getName().equals(MICROMETER_SCOPE))
        .extracting(MetricData::getName)
        .doesNotContain(
            "jvm.memory.used",
            "jvm.memory.committed",
            "jvm.memory.max",
            "jvm.threads.live",
            "jvm.threads.daemon",
            "jvm.threads.states",
            "system.cpu.count");
  }

  @Test
  void gcTimersAreSuppressedOnlyInTheBridge() {
    // Exercise companion creation as well as the timer itself if suppression stops working.
    Metrics.globalRegistry
        .config()
        .meterFilter(
            new MeterFilter() {
              @Override
              public DistributionStatisticConfig configure(
                  Meter.Id id, DistributionStatisticConfig config) {
                if (id.getName().equals("jvm.gc.pause")
                    || id.getName().equals("jvm.gc.concurrent.phase.time")) {
                  return DistributionStatisticConfig.builder()
                      .percentiles(0.5)
                      .percentilesHistogram(true)
                      .build()
                      .merge(config);
                }
                return config;
              }
            });
    SimpleMeterRegistry otherRegistry = new SimpleMeterRegistry();
    try (JvmGcMetrics gcMetrics = new JvmGcMetrics()) {
      Metrics.addRegistry(otherRegistry);
      try {
        gcMetrics.bindTo(Metrics.globalRegistry);
        // Exercise concurrent-phase suppression independently of the JVM collector.
        Timer concurrentPhase =
            Timer.builder("jvm.gc.concurrent.phase.time").register(Metrics.globalRegistry);
        concurrentPhase.record(Duration.ofMillis(7));
        assertThat(concurrentPhase.count()).isEqualTo(1);
        assertThat(otherRegistry.get("jvm.gc.concurrent.phase.time").timer().count()).isEqualTo(1);
        System.gc();
        // An empty bridge export is meaningful only after Micrometer received a GC notification.
        await()
            .untilAsserted(
                () ->
                    assertThat(otherRegistry.find("jvm.gc.pause").timers())
                        .anySatisfy(timer -> assertThat(timer.count()).isPositive()));
        assertThat(Metrics.globalRegistry.find("jvm.gc.pause").timers())
            .anySatisfy(timer -> assertThat(timer.count()).isPositive());
        testing.waitAndAssertMetrics(
            RUNTIME_TELEMETRY_SCOPE,
            "jvm.gc.duration",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric.getHistogramData().getPoints())
                            .anySatisfy(point -> assertThat(point.getCount()).isPositive())));
        assertThat(testing.metrics())
            .filteredOn(
                metric -> metric.getInstrumentationScopeInfo().getName().equals(MICROMETER_SCOPE))
            .extracting(MetricData::getName)
            .noneMatch(
                name ->
                    name.startsWith("jvm.gc.pause")
                        || name.startsWith("jvm.gc.concurrent.phase.time"));
      } finally {
        Metrics.removeRegistry(otherRegistry);
        otherRegistry.close();
      }
    }
  }
}
