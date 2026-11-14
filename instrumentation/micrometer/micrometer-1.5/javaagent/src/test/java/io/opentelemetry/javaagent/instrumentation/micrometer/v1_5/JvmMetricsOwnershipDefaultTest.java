/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JvmMetricsOwnershipDefaultTest {
  private static final String MICROMETER_SCOPE = "io.opentelemetry.micrometer-1.5";
  private static final String RUNTIME_TELEMETRY_SCOPE = "io.opentelemetry.runtime-telemetry";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @AfterEach
  void cleanup() {
    Metrics.globalRegistry.forEachMeter(Metrics.globalRegistry::remove);
  }

  @Test
  void jvmMetricsAreBridgedByDefault() {
    new ClassLoaderMetrics().bindTo(Metrics.globalRegistry);

    testing.waitAndAssertMetrics(
        RUNTIME_TELEMETRY_SCOPE, "jvm.class.count", AbstractIterableAssert::isNotEmpty);
    testing.waitAndAssertMetrics(
        MICROMETER_SCOPE, "jvm.classes.loaded", AbstractIterableAssert::isNotEmpty);
  }
}
