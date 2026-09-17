/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Experimental;
import org.junit.jupiter.api.Test;

/**
 * Tests the base (Java 8) source set, where JFR is not available and the JFR configuration methods
 * are expected to be no-ops rather than failures.
 */
class RuntimeTelemetryBuilderJfrUnavailableTest {

  @Test
  void setJfrMetricsIsNoopWithoutJfr() {
    RuntimeTelemetryBuilder builder = RuntimeTelemetry.builder(OpenTelemetry.noop());

    IncludeExclude selector =
        IncludeExclude.builder().setIncluded(singletonList("jvm.cpu.longlock")).build();
    assertThatCode(() -> Experimental.setJfrMetrics(builder, selector)).doesNotThrowAnyException();

    try (RuntimeTelemetry runtimeTelemetry = builder.build()) {
      assertThat(runtimeTelemetry.getJfrTelemetry()).isNull();
    }
  }
}
