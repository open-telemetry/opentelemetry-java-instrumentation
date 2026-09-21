/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StartupTimingsTest {
  @Test
  void parsesThePinnedSpringLogFormat() {
    StartupTimings timings =
        StartupTimings.parse(
            "INFO Started SpringbootApplication in 1.25 seconds (JVM running for 2.5)\n");

    assertThat(timings).isEqualTo(new StartupTimings(1.25, 2.5));
  }

  @ParameterizedTest
  @MethodSource("invalidLogs")
  void rejectsMissingMalformedDuplicateAndInvalidTimings(String logs) {
    assertThatThrownBy(() -> StartupTimings.parse(logs))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static Stream<Arguments> invalidLogs() {
    return Stream.of(
        Arguments.argumentSet("missing", "Spring is ready"),
        Arguments.argumentSet(
            "malformed", "Started SpringbootApplication in 1.25 seconds (process running for 2.5)"),
        Arguments.argumentSet(
            "duplicate",
            "Started SpringbootApplication in 1.25 seconds (JVM running for 2.5)\n"
                + "Started SpringbootApplication in 1.30 seconds (JVM running for 2.6)"),
        Arguments.argumentSet(
            "jvm shorter", "Started SpringbootApplication in 2.5 seconds (JVM running for 1.25)"),
        Arguments.argumentSet(
            "zero", "Started SpringbootApplication in 0 seconds (JVM running for 1.25)"));
  }
}
