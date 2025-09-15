/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.logs.data.LogRecordData;
import java.time.Duration;
import java.util.Collection;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class LogsSmokeTest extends JavaSmokeTest {
  @Override
  protected String getTargetImage(String jdk) {
    return "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot:jdk"
        + jdk
        + "-20211213.1570880324";
  }

  @Override
  protected TargetWaitStrategy getWaitStrategy() {
    return new TargetWaitStrategy.Log(
        Duration.ofMinutes(1), ".*Started SpringbootApplication in.*");
  }

  @ParameterizedTest
  @ValueSource(ints = {8, 11, 17})
  void shouldExportLogs(int jdk) throws Exception {
    withTarget(jdk, () -> {
      client().get("/greeting").aggregate().join();
      Collection<LogRecordData> logs = waitForLogs();

      assertThat(logs).isNotEmpty();
    });
  }
}
