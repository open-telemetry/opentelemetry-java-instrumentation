/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class LogbackTest extends AbstractLogbackTest {

  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  @BeforeAll
  static void setupAll() {
    // by default LoggerContext contains HOSTNAME property we clear it to start with empty context
    Helper.resetLoggerContext();
  }

  @BeforeEach
  void setUp() {
    OpenTelemetryAppender.install(testing.getOpenTelemetry());
  }

  @AfterEach
  void tearDown() {
    OpenTelemetryAppender.install(null);
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected boolean expectThreadAttributes() {
    return false;
  }
}
