/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class Log4j2Test extends AbstractLog4j2Test {

  @RegisterExtension
  static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  @BeforeEach
  void setup() {
    OpenTelemetryAppender.install(testing.getOpenTelemetry());
  }

  @AfterAll
  static void cleanup() {
    OpenTelemetryAppender.install(null);
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }
}
