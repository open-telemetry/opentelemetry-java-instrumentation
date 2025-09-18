/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class SecurityManagerSmokeTest extends JavaSmokeTest {

  public SecurityManagerSmokeTest() {
    super(
        SmokeTestTarget.builder(
                jdk ->
                    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-security-manager:jdk"
                        + jdk
                        + "-20250915.17728045123")
            .env("OTEL_JAVAAGENT_EXPERIMENTAL_SECURITY_MANAGER_SUPPORT_ENABLED", "true"));
  }

  @ParameterizedTest
  @ValueSource(ints = {8, 11, 17, 21, 23})
  void securityManagerSmokeTest(int jdk) {
    startTarget(jdk);
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("test")));
  }
}
