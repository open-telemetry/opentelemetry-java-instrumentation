/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static io.opentelemetry.sdk.testing.assertj.TracesAssert.assertThat;

import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
public class SecurityManagerSmokeTest extends JavaSmokeTest {
  @Override
  protected String getTargetImage(String jdk) {
    return "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-security-manager:jdk"
        + jdk
        + "-20241021.11448062560";
  }

  @Override
  protected Map<String, String> getExtraEnv() {
    return Collections.singletonMap(
        "OTEL_JAVAAGENT_EXPERIMENTAL_SECURITY_MANAGER_SUPPORT_ENABLED", "true");
  }

  @Test
  public void security_manager_smoke_test_on_JDK__jdk(int jdk) {
    startTarget(jdk);

    assertThat(waitForTraces())
        .hasTracesSatisfyingExactly(
            trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("test")));

    stopTarget();

    //    where: DefaultGroovyMethods.leftShift(jdk, new ArrayList<Integer>(Arrays.asList(8, 11, 17,
    // 21, 23)));
  }
}
