/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class SdkDisabledSmokeTest extends AbstractSmokeTest<Integer> {

  @Override
  protected void configure(SmokeTestOptions<Integer> options) {
    options.springBoot().env("OTEL_SDK_DISABLED", "true").telemetryTimeout(Duration.ofSeconds(5));
  }

  @ParameterizedTest
  @ValueSource(ints = {8, 11, 17, 21, 25})
  void noopSdkSmokeTest(int jdk) {
    SmokeTestOutput output = start(jdk);
    assertThat(client().get("/greeting").aggregate().join().contentUtf8()).isEqualTo("Hi!");
    assertThat(testing.spans()).isEmpty();
    output.assertAgentVersionLogged();
    assertThat(testing.spans()).isEmpty();
  }
}
