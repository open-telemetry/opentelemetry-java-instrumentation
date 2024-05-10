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

  @Autowired protected OpenTelemetry openTelemetry;

  protected SpringSmokeTestRunner testing;

  @BeforeEach
  void setUpTesting() {
    testing = new SpringSmokeTestRunner(openTelemetry);
  }

  @AfterEach
  void checkSpringLogs(CapturedOutput output) {
    // warnings are emitted if the auto-configuration have non-fatal problems
    assertThat(output)
        // not a warning in Spring Boot 2
        .doesNotContain("is not eligible for getting processed by all BeanPostProcessors")
        // only look for WARN and ERROR log level, e.g. [Test worker] WARN
        .doesNotContain("] WARN")
        .satisfies(
            s -> {
              if (!s.toString()
                  .contains(
                      "Unable to load io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider")) {
                assertThat(s).doesNotContain("] ERROR");
              }
            });
  }
}
