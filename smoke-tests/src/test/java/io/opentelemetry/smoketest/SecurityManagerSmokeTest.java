/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class SecurityManagerSmokeTest extends JavaSmokeTest {
  @RegisterExtension static final InstrumentationExtension testing = SmokeTestInstrumentationExtension.create();
  @RegisterExtension static final AutoCleanupExtension autoCleanup = AutoCleanupExtension.create();

  @Override
  protected String getTargetImage(String jdk) {
    return "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-security-manager:jdk"
        + jdk
        + "-20250915.17728045123";
  }

  @Override
  protected Map<String, String> getExtraEnv() {
    return Collections.singletonMap(
        "OTEL_JAVAAGENT_EXPERIMENTAL_SECURITY_MANAGER_SUPPORT_ENABLED", "true");
  }

  @ParameterizedTest
  @ValueSource(ints = {8, 11, 17, 21, 23})
  void securityManagerSmokeTest(int jdk) {
    startTarget(jdk);
    autoCleanup.deferCleanup(this::stopTarget);

    testing.waitAndAssertTraces(trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("test")));
  }
}
