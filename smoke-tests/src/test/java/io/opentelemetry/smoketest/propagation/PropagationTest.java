/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.propagation;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.smoketest.JavaSmokeTest;
import io.opentelemetry.smoketest.SmokeTestTarget;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import org.junit.jupiter.api.Test;

public abstract class PropagationTest extends JavaSmokeTest {

  public PropagationTest() {
    super(SmokeTestTarget.springBoot("20211213.1570880324"));
  }

  @Test
  public void shouldPropagate() {
    startTarget(11);
    AggregatedHttpResponse response = client().get("/front").aggregate().join();

    var traceId = testing.waitForTraces(1).get(0).get(0).getTraceId();
    assertThat(response.contentUtf8()).isEqualTo(traceId + ";" + traceId);
  }
}
