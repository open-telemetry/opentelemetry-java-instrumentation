/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class AgentDebugLoggingTest extends JavaSmokeTest {

  public AgentDebugLoggingTest() {
      super(
        SmokeTestTarget.springBoot("20250915.17728045097")
            .waitStrategy(
                new TargetWaitStrategy.Log(
                    Duration.ofMinutes(1),
                    ".*DEBUG io.opentelemetry.javaagent.tooling.VersionLogger.*")));
  }

  @DisplayName("verifies that debug logging is working by checking for a debug log on startup")
  @Test
  void verifyLogging() {
    startTarget(8);
  }
}
