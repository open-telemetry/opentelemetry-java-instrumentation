/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class SecurityManagerSmokeTest extends AbstractSmokeTest<Integer> {

  @Override
  protected void configure(SmokeTestOptions<Integer> options) {
    options
        .image(
            jdk ->
                String.format(
                    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-security-manager:jdk%s-%s",
                    jdk, ImageVersions.SECURITY_MANAGER_VERSION))
        .env("OTEL_JAVAAGENT_EXPERIMENTAL_SECURITY_MANAGER_SUPPORT_ENABLED", "true");
  }

  @ParameterizedTest
  @ValueSource(ints = {8, 11, 17, 21}) // Security Manager removed in Java 25
  void securityManagerSmokeTest(int jdk) {
    start(jdk);
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("test")));
  }
}
