/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
public abstract class AbstractSpringStarterSmokeTest {

  @Autowired OpenTelemetry openTelemetry;

  protected SpringSmokeTestRunner testing;

  @BeforeEach
  void initOpenTelemetry() {
    testing = new SpringSmokeTestRunner(openTelemetry);
  }

  @AfterEach
  void checkSpringLogs(CapturedOutput output) {
    // warnings are emitted if the auto-configuration have non-fatal problems
    assertThat(output).doesNotContain("WARN").doesNotContain("ERROR");
  }
}
