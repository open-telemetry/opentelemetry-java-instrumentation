/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.mdc.v1_0;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class LogbackTest extends AbstractLogbackTest {

  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension getInstrumentationExtension() {
    return testing;
  }

  @Test
  void testMdcMutable() {
    TestAppender testAppender = TestAppender.getInstance();
    runWithSpanAndBaggage("test", baggage, () -> logger.info("log message"));

    ILoggingEvent lastEvent = testAppender.getLastEvent();
    assertThat(lastEvent.getMessage()).isEqualTo("log message");
    Map<String, String> map = lastEvent.getMDCPropertyMap();
    // verify that mdc map associated with the event is mutable
    map.put("test", "test");
  }
}
