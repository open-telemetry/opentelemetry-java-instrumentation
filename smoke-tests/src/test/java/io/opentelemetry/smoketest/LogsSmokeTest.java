/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.logs.data.LogRecordData;
import java.util.Collection;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class LogsSmokeTest extends JavaSmokeTest {

  public LogsSmokeTest() {
    super(SmokeTestTarget.springBoot("20211213.1570880324"));
  }

  @ParameterizedTest
  @ValueSource(ints = {8, 11, 17})
  void shouldExportLogs(int jdk) {
    startTarget(jdk);
    client().get("/greeting").aggregate().join();
    Collection<LogRecordData> logs = testing.logRecords();

    assertThat(logs).isNotEmpty();
  }
}
