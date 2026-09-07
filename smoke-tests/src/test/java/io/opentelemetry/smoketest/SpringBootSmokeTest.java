/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class SpringBootSmokeTest extends AbstractSpringBootSmokeTest {

  @ParameterizedTest
  @ValueSource(ints = {8, 11, 17, 21, 25})
  void springBootSmokeTest(int jdk) {
    SmokeTestOutput output = start(jdk);

    assertSpringBootTelemetry(output);
  }
}
