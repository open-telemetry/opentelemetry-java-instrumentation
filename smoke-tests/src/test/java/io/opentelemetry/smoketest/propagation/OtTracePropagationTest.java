/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.propagation;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.smoketest.SmokeTestInstrumentationExtension;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class OtTracePropagationTest {

  @RegisterExtension
  static final SmokeTestInstrumentationExtension testing =
      SmokeTestInstrumentationExtension.springBoot("20211213.1570880324")
          .env("otel.propagators", "ottrace")
          .build();

  @Test
  public void shouldPropagate() {
    testing.start(11);
    AggregatedHttpResponse response = testing.client().get("/front").aggregate().join();
    var traceId = testing.waitForTraces(1).get(0).get(0).getTraceId().substring(16);

    assertThat(response.contentUtf8()).matches("[0-9a-f]{16}" + traceId + ";[0]{16}" + traceId);
  }
}
