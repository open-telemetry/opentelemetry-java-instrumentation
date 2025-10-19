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
class LogsSmokeTest extends AbstractSmokeTest<Integer> {

  @Override
  protected void configure(SmokeTestOptions<Integer> options) {
    options.springBoot("20251017.18602659902");
  }

  @ParameterizedTest
  @ValueSource(ints = {8, 11, 17, 21, 25})
  void shouldExportLogs(int jdk) {
    start(jdk);
    client().get("/greeting").aggregate().join();
    Collection<LogRecordData> logs = testing.logRecords();

    assertThat(logs).isNotEmpty();
  }
}
