/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.status.Status;
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
    OpenTelemetryAppender.install(testing.getOpenTelemetry());
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    assertThat(loggerContext.getStatusManager().getCopyOfStatusList())
        .filteredOn(status -> status.getLevel() == Status.WARN)
        .filteredOn(status -> status.getOrigin() instanceof OpenTelemetryAppender)
        .filteredOn(status -> status.getMessage().contains("is downstream of AsyncAppender"))
        .singleElement()
        .satisfies(
            status ->
                assertThat(status.getMessage())
                    .contains(
                        "OpenTelemetryAppender 'OpenTelemetry' is downstream of AsyncAppender"
                            + " 'AsyncAppender'"));
    logger.info("log message 1");

    testing.waitAndAssertLogRecords(logRecord -> logRecord.hasBody("log message 1"));
  }
}
