/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.MetricData;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

// Runs on Java 17+ with class metrics selected through JFR and other metrics left on JMX.
class JvmMetricsOwnershipJfrTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void jfrClassMetricsRemainBridgedWhileJmxMemoryMetricsAreSuppressed() {
    new ClassLoaderMetrics().bindTo(Metrics.globalRegistry);
    new JvmMemoryMetrics().bindTo(Metrics.globalRegistry);

    // A positive value proves JFR delivered an event, rather than just creating an instrument.
    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-telemetry",
        "jvm.class.count",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric.getLongSumData().getPoints())
                        .anySatisfy(point -> assertThat(point.getValue()).isPositive())));
    testing.waitAndAssertMetrics(
        "io.opentelemetry.micrometer-1.5",
        "jvm.classes.loaded",
        AbstractIterableAssert::isNotEmpty);
    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-telemetry",
        "jvm.memory.used",
        AbstractIterableAssert::isNotEmpty);
    assertThat(testing.metrics())
        .filteredOn(
            metric ->
                metric
                    .getInstrumentationScopeInfo()
                    .getName()
                    .equals("io.opentelemetry.micrometer-1.5"))
        .extracting(MetricData::getName)
        .doesNotContain("jvm.memory.used", "jvm.memory.committed", "jvm.memory.max");
  }
}
