/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

class LogbackMissingTest {

  @Test
  void applicationStartsWhenLogbackIsMissing() {
    // verify that logback is not present
    Assertions.assertThrows(
        ClassNotFoundException.class, () -> Class.forName("ch.qos.logback.core.Appender"));

    SpringApplication app = new SpringApplication(OpenTelemetryAppenderAutoConfiguration.class);
    try (ConfigurableApplicationContext ignore = app.run()) {
      // do nothing
    }
  }
}
