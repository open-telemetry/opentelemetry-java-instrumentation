/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
public class AgentDebugLoggingTest extends JavaSmokeTest {
  @Override
  protected String getTargetImage(String jdk) {
    return "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot:jdk"
        + jdk
        + "-20211213.1570880324";
  }

  @Override
  protected TargetWaitStrategy getWaitStrategy() {
    return new TargetWaitStrategy.Log(
        Duration.ofMinutes(1), ".*DEBUG io.opentelemetry.javaagent.tooling.VersionLogger.*");
  }

  @Test
  public void verify_that_debug_logging_is_working() {
    startTarget(8);
    stopTarget();
  }
}
