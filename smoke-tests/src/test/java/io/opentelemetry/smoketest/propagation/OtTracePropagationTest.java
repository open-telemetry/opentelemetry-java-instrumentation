/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.propagation;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.smoketest.JavaSmokeTest;
import io.opentelemetry.smoketest.TargetWaitStrategy;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class OtTracePropagationTest extends JavaSmokeTest {
  @Override
  protected String getTargetImage(String jdk) {
    return "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot:jdk"
        + jdk
        + "-20211213.1570880324";
  }

  @Override
  protected TargetWaitStrategy getWaitStrategy() {
    return new TargetWaitStrategy.Log(
        Duration.ofMinutes(1), ".*Started SpringbootApplication in.*");
  }

  @Test
  public void shouldPropagate() {
    startTarget(11);
    AggregatedHttpResponse response = client().get("/front").aggregate().join();
    var traceId = testing.waitForTraces(1).get(0).get(0).getTraceId();

    assertThat(response.contentUtf8()).matches("[0-9a-f]{16}" + traceId + ";[0]{16}" + traceId);
  }

  @Override
  protected Map<String, String> getExtraEnv() {
    return Map.of("otel.propagators", "ottrace");
  }
}
