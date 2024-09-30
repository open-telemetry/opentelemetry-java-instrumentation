/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DurationUtilTest {

  @Test
  void convertDurationToSeconds() {
    Duration duration = Duration.ofSeconds(7, 144);
    double seconds = DurationUtil.toSeconds(duration);
    assertThat(seconds).isEqualTo(7.000000144);
  }
}
