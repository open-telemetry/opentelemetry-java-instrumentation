/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.lang.management.OperatingSystemMXBean;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CpuTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Mock private OperatingSystemMXBean osBean;

  @Test
  void registerObservers() {
    when(osBean.getSystemLoadAverage()).thenReturn(2.2);
    Supplier<Double> systemCpuUsage = () -> 0.11;
    Supplier<Double> processCpuUsage = () -> 0.05;

    Cpu.INSTANCE.registerObservers(
        testing.getOpenTelemetry(), osBean, systemCpuUsage, processCpuUsage);

    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-metrics",
        "process.runtime.jvm.system.cpu.load_1m",
        metrics ->
            metrics.anySatisfy(
                metricData ->
                    assertThat(metricData)
                        .hasDescription("Average CPU load of the whole system for the last minute")
                        .hasUnit("1")
                        .hasDoubleGaugeSatisfying(
                            gauge -> gauge.hasPointsSatisfying(point -> point.hasValue(2.2)))));
    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-metrics",
        "process.runtime.jvm.system.cpu.utilization",
        metrics ->
            metrics.anySatisfy(
                metricData ->
                    assertThat(metricData)
                        .hasDescription("Recent cpu utilization for the whole system")
                        .hasUnit("1")
                        .hasDoubleGaugeSatisfying(
                            gauge -> gauge.hasPointsSatisfying(point -> point.hasValue(0.11)))));
    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-metrics",
        "process.runtime.jvm.cpu.utilization",
        metrics ->
            metrics.anySatisfy(
                metricData ->
                    assertThat(metricData)
                        .hasDescription("Recent cpu utilization for the process")
                        .hasUnit("1")
                        .hasDoubleGaugeSatisfying(
                            gauge -> gauge.hasPointsSatisfying(point -> point.hasValue(0.05)))));
  }
}
