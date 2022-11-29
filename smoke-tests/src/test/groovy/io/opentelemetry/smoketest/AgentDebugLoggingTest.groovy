/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import spock.lang.IgnoreIf

import java.time.Duration

import static io.opentelemetry.smoketest.TestContainerManager.useWindowsContainers

@IgnoreIf({ useWindowsContainers() })
class AgentDebugLoggingTest extends SmokeTest {

  protected String getTargetImage(String jdk) {
    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot:jdk$jdk-20211213.1570880324"
  }

  @Override
  protected TargetWaitStrategy getWaitStrategy() {
    return new TargetWaitStrategy.Log(Duration.ofMinutes(1), ".*DEBUG io.opentelemetry.javaagent.tooling.VersionLogger.*")
  }

  def "verify that debug logging is working"() {
    setup:
    startTarget(8)

    cleanup:
    stopTarget()
  }
}
