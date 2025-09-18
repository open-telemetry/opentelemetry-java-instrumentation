/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.propagation;

import io.opentelemetry.smoketest.JavaSmokeTest;
import io.opentelemetry.smoketest.SmokeTestTarget;
import io.opentelemetry.smoketest.TargetWaitStrategy;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class OtTracePropagationTest extends JavaSmokeTest {

  public OtTracePropagationTest() {
    super(
        SmokeTestTarget.builder(
                jdk ->
                    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot:jdk"
                        + jdk
                        + "-20211213.1570880324")
            .waitStrategy(
                new TargetWaitStrategy.Log(
                    Duration.ofMinutes(1), ".*Started SpringbootApplication in.*"))
            .env("otel.propagators", "ottrace"));
  }

  @Test
  public void shouldPropagate() {
    startTarget(11);
    AggregatedHttpResponse response = client().get("/front").aggregate().join();
    var traceId = testing.waitForTraces(1).get(0).get(0).getTraceId().substring(16);

    assertThat(response.contentUtf8()).matches("[0-9a-f]{16}" + traceId + ";[0]{16}" + traceId);
  }
}
