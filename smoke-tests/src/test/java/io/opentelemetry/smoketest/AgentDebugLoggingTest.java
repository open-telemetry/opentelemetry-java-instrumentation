/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class AgentDebugLoggingTest {

  @RegisterExtension
  static final SmokeTestInstrumentationExtension testing =
      SmokeTestInstrumentationExtension.springBoot("20250915.17728045097")
          .waitStrategy(
              new TargetWaitStrategy.Log(
                  Duration.ofMinutes(1),
                  ".*DEBUG io.opentelemetry.javaagent.tooling.VersionLogger.*"))
          .build();

  @DisplayName("verifies that debug logging is working by checking for a debug log on startup")
  @Test
  void verifyLogging() {
    testing.start(8);
  }
}
