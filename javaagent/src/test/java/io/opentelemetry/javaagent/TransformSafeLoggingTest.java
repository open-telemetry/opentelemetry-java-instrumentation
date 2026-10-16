/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

import jvmbootstraptest.TransformSafeLoggingChecker;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TransformSafeLoggingTest {

  @ParameterizedTest
  @ValueSource(strings = {"default", "false", "true"})
  void routesOrdinaryJulLogging(String enabled) throws Exception {
    String[] jvmArgs =
        enabled.equals("default")
            ? new String[] {"-Dotel.javaagent.debug=true"}
            : new String[] {
              "-Dotel.javaagent.debug=true",
              "-Dotel.javaagent.testing.transform-safe-logging.enabled=" + enabled
            };
    int exitCode =
        IntegrationTestUtils.runOnSeparateJvm(
            TransformSafeLoggingChecker.class.getName(),
            jvmArgs,
            new String[] {enabled},
            emptyMap(),
            true);
    assertThat(exitCode).isZero();
  }
}
