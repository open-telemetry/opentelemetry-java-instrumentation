/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.sdk.metrics.data.MetricData;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class SpringBootSmokeTest extends AbstractSpringBootSmokeTest {

  @ParameterizedTest
  @ValueSource(ints = {8, 11, 17, 21, 25, 27})
  void springBootSmokeTest(int jdk) {
    SmokeTestOutput output = start(jdk);

    assertSpringBootTelemetry(output);

    await()
        .untilAsserted(
            () ->
                assertThat(testing.metrics())
                    .extracting(MetricData::getName)
                    .contains(
                        "otel.sdk.exporter.span.exported", "otel.sdk.processor.span.processed"));
  }
}
