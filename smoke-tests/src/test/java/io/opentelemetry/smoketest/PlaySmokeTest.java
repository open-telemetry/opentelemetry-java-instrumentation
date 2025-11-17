/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.semconv.HttpAttributes;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class PlaySmokeTest extends AbstractSmokeTest<Integer> {

  @Override
  protected void configure(SmokeTestOptions<Integer> options) {
    options
        .image(
            jdk ->
                String.format(
                    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-play:jdk%s-%s",
                    jdk, TestImageVersions.PLAY_VERSION))
        .waitStrategy(
            new TargetWaitStrategy.Log(java.time.Duration.ofMinutes(1), ".*Listening for HTTP.*"));
  }

  @ParameterizedTest
  @ValueSource(ints = {17, 21, 25})
  void playSmokeTest(int jdk) {
    start(jdk);

    var response = client().get("/welcome?id=1").aggregate().join();

    assertThat(response.contentUtf8()).isEqualTo("Welcome 1.");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET /welcome")
                        .hasKind(SpanKind.SERVER)
                        .hasAttribute(HttpAttributes.HTTP_ROUTE, "/welcome"),
                span -> span.hasName("/welcome").hasKind(SpanKind.INTERNAL)));
  }
}
