/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.JvmAttributes.JVM_GC_ACTION;
import static io.opentelemetry.semconv.JvmAttributes.JVM_GC_NAME;
import static io.opentelemetry.semconv.incubating.JvmIncubatingAttributes.JVM_GC_CAUSE;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RuntimeTelemetryDefaultsTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void capturesGcCause(boolean useBuilder) {
    try (RuntimeTelemetry telemetry =
        useBuilder
            ? RuntimeTelemetry.builder(testing.getOpenTelemetry()).build()
            : RuntimeTelemetry.create(testing.getOpenTelemetry())) {
      assertThat(telemetry.getJfrTelemetry()).isNull();
      System.gc();

      testing.waitAndAssertMetrics(
          "io.opentelemetry.runtime-telemetry",
          metric ->
              metric
                  .hasName("jvm.gc.duration")
                  .hasHistogramSatisfying(
                      histogram ->
                          histogram.hasPointsSatisfying(
                              point ->
                                  point.hasAttributesSatisfyingExactly(
                                      satisfies(JVM_GC_NAME, val -> val.isNotBlank()),
                                      satisfies(JVM_GC_ACTION, val -> val.isNotBlank()),
                                      satisfies(JVM_GC_CAUSE, val -> val.isNotBlank())))));
    }
  }
}
