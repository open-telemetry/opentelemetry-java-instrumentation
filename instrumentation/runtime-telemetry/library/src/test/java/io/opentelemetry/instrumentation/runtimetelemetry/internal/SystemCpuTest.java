/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

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
class SystemCpuTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Mock private OperatingSystemMXBean osBean;

  @Test
  void registerObservers() {
    when(osBean.getSystemLoadAverage()).thenReturn(2.2);
    Supplier<Double> systemCpuUtilization = () -> 0.11;

    SystemCpu.registerObservers(
        testing.getOpenTelemetry().getMeter("test"), osBean, systemCpuUtilization);

    testing.waitAndAssertMetrics(
        "test",
        "jvm.system.cpu.load_1m",
        metrics ->
            metrics.anySatisfy(
                metricData ->
                    assertThat(metricData)
                        .hasDescription(
                            "Average CPU load of the whole system for the last minute as reported by the JVM.")
                        .hasUnit("{run_queue_item}")
                        .hasDoubleGaugeSatisfying(
                            gauge -> gauge.hasPointsSatisfying(point -> point.hasValue(2.2)))));
    testing.waitAndAssertMetrics(
        "test",
        "jvm.system.cpu.utilization",
        metrics ->
            metrics.anySatisfy(
                metricData ->
                    assertThat(metricData)
                        .hasDescription(
                            "Recent CPU utilization for the whole system as reported by the JVM.")
                        .hasUnit("1")
                        .hasDoubleGaugeSatisfying(
                            gauge -> gauge.hasPointsSatisfying(point -> point.hasValue(0.11)))));
  }
}
