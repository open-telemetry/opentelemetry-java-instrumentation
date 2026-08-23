/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import org.junit.jupiter.api.condition.DisabledIf;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class SecurityManagerIndySmokeTest extends SecurityManagerSmokeTest {

  @Override
  protected void configure(SmokeTestOptions<Integer> options) {
    super.configure(options);
    options.env("OTEL_JAVAAGENT_EXPERIMENTAL_INDY", "true");
  }
}
