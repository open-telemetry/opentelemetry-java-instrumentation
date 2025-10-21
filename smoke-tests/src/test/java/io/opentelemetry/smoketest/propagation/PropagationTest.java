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

abstract class PropagationTest extends AbstractSmokeTest<Integer> {

  @Override
  protected void configure(SmokeTestOptions<Integer> options) {
    options.springBoot("20251017.18602659902");
  }

  @Test
  void shouldPropagate() {
    start(11);
    AggregatedHttpResponse response = client().get("/front").aggregate().join();

    var traceId = testing.waitForTraces(1).get(0).get(0).getTraceId();
    assertThat(response.contentUtf8()).isEqualTo(traceId + ";" + traceId);
  }
}
