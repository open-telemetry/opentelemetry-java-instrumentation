/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

class LogbackMissingTest {

  @Test
  void applicationStartsWhenLogbackIsMissing() {
    // verify that logback is not present
    assertThatThrownBy(() -> Class.forName("ch.qos.logback.core.Appender"))
        .isInstanceOf(ClassNotFoundException.class);

    SpringApplication app = new SpringApplication(OpenTelemetryAppenderAutoConfiguration.class);
    try (ConfigurableApplicationContext ignore = app.run()) {
      // do nothing
    }
  }
}
