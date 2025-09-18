/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.propagation;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.testing.assertj.TracesAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.smoketest.JavaSmokeTest;
import io.opentelemetry.smoketest.TargetWaitStrategy;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

public abstract class PropagationTest extends JavaSmokeTest {

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
  public void shouldPropagate() throws Exception {
    startTarget(11);
    AggregatedHttpResponse response = client().get("/front").aggregate().join();
    List<SpanData> traces = waitForTraces();

    TracesAssert.assertThat(traces).hasTracesSatisfyingExactly(trace -> {});

    var traceId = traces.get(0).getTraceId();
    assertThat(response.contentUtf8()).isEqualTo(traceId + ";" + traceId);
  }
}
