/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

import jvmbootstraptest.ApplicationLoggingChecker;
import org.junit.jupiter.api.Test;

class ApplicationLoggingTest {

  @Test
  void suppressesAgentJulLoggingDuringTransformation() throws Exception {
    int exitCode =
        IntegrationTestUtils.runOnSeparateJvm(
            ApplicationLoggingChecker.class.getName(),
            new String[] {
              "-Dotel.javaagent.logging=application",
              "-Dotel.traces.exporter=none",
              "-Dotel.metrics.exporter=none",
              "-Dotel.logs.exporter=none"
            },
            new String[0],
            emptyMap(),
            true);
    assertThat(exitCode).isZero();
  }
}
