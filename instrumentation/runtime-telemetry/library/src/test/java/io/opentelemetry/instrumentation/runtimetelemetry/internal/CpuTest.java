/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import static java.util.concurrent.TimeUnit.SECONDS;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CpuTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void registerObservers() {
    Supplier<Long> processCpuTime = () -> SECONDS.toNanos(42);
    Supplier<Double> processCpuUtilization = () -> 0.05;

    Cpu.INSTANCE.registerObservers(
        testing.getOpenTelemetry().getMeter("test"), processCpuTime, processCpuUtilization);

    testing.waitAndAssertMetrics(
        "test",
        metric ->
            metric
                .hasName("jvm.cpu.time")
                .hasDescription("CPU time used by the process as reported by the JVM.")
                .hasUnit("s")
                .hasDoubleSumSatisfying(
                    count -> count.isMonotonic().hasPointsSatisfying(point -> point.hasValue(42))));
    testing.waitAndAssertMetrics(
        "test",
        metric ->
            metric
                .hasName("jvm.cpu.recent_utilization")
                .hasDescription("Recent CPU utilization for the process as reported by the JVM.")
                .hasUnit("1")
                .hasDoubleGaugeSatisfying(
                    gauge -> gauge.hasPointsSatisfying(point -> point.hasValue(0.05))));
  }
}
