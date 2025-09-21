/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class SecurityManagerSmokeTest {

  @RegisterExtension
  static final SmokeTestInstrumentationExtension<Integer> testing =
      SmokeTestInstrumentationExtension.<Integer>builder(
              jdk ->
                  String.format(
                      "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-security-manager:jdk%s-20250915.17728045123",
                      jdk))
          .env("OTEL_JAVAAGENT_EXPERIMENTAL_SECURITY_MANAGER_SUPPORT_ENABLED", "true")
          .build();

  @ParameterizedTest
  @ValueSource(ints = {8, 11, 17, 21, 23})
  void securityManagerSmokeTest(int jdk) {
    testing.start(jdk);
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("test")));
  }
}
