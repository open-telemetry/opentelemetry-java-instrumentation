/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class AsyncOpenTelemetryAppenderTest {
  private static final Logger logger = LoggerFactory.getLogger("TestLogger");

  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  @BeforeEach
  void setup() {
    OpenTelemetryAppender.install(testing.getOpenTelemetry());
  }

  @Test
  void captureLogMessage() {
    logger.info("log message 1");

    testing.waitAndAssertLogRecords(logRecord -> logRecord.hasBody("log message 1"));
  }
}
