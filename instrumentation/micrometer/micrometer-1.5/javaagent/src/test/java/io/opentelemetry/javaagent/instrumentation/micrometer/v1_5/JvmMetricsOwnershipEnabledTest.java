/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.MetricData;
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
}
