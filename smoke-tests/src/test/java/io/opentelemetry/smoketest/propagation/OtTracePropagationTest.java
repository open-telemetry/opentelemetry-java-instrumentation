/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.propagation;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.smoketest.AbstractSmokeTest;
import io.opentelemetry.smoketest.SmokeTestOptions;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class OtTracePropagationTest extends AbstractSmokeTest<Integer> {

  @Override
  protected void configure(SmokeTestOptions<Integer> options) {
    options.springBoot("20251011.18424653812").env("otel.propagators", "ottrace");
  }

  @Test
  void shouldPropagate() {
    start(11);
    AggregatedHttpResponse response = client().get("/front").aggregate().join();
    var traceId = testing.waitForTraces(1).get(0).get(0).getTraceId().substring(16);

    assertThat(response.contentUtf8()).matches("[0-9a-f]{16}" + traceId + ";[0]{16}" + traceId);
  }
}
