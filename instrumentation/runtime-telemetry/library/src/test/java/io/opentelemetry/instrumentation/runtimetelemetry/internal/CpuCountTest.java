/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CpuCountTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void registerObservers() {
    IntSupplier availableProcessors = () -> 8;

    CpuCount.INSTANCE.registerObservers(
        testing.getOpenTelemetry().getMeter("test"), availableProcessors);

    testing.waitAndAssertMetrics(
        "test",
        metric ->
            metric
                .hasName("jvm.cpu.count")
                .hasDescription("Number of processors available to the Java virtual machine.")
                .hasUnit("{cpu}")
                .hasLongSumSatisfying(
                    count ->
                        count.isNotMonotonic().hasPointsSatisfying(point -> point.hasValue(8))));
  }
}
