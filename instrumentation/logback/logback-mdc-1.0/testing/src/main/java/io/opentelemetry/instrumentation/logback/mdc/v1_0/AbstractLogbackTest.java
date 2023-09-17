/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.mdc.v1_0;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.read.ListAppender;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Scope;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractLogbackTest {

  protected static final Logger logger = LoggerFactory.getLogger("test");

  protected static ListAppender<ILoggingEvent> listAppender = new ListAppender<>();

  @BeforeAll
  static void setUp() {
    ch.qos.logback.classic.Logger logbackLogger = (ch.qos.logback.classic.Logger) logger;
    Appender<ILoggingEvent> topLevelListAppender = logbackLogger.getAppender("LIST");
    if (topLevelListAppender != null) {
      // Auto instrumentation test
      listAppender = (ListAppender<ILoggingEvent>) topLevelListAppender;
    } else {
      // Library instrumentation test.
      OpenTelemetryAppender otelAppender =
          (OpenTelemetryAppender) logbackLogger.getAppender("OTEL");
      listAppender = (ListAppender<ILoggingEvent>) otelAppender.getAppender("LIST");
    }
  }

  @BeforeEach
  void clearList() {
    listAppender.list.clear();
  }

  @Test
  void testNoIdsWhenNoSpan() {
    Baggage baggage = Baggage.empty().toBuilder().put("baggage_key", "baggage_value").build();

    runWithBaggage(
        baggage,
        () -> {
          logger.info("log message 1");
          logger.info("log message 2");
        });

    List<ILoggingEvent> events = listAppender.list;

    assertThat(events.size()).isEqualTo(2);
    assertThat(events.get(0).getMessage()).isEqualTo("log message 1");
    assertThat(events.get(0).getMDCPropertyMap().get("trace_id")).isNull();
    assertThat(events.get(0).getMDCPropertyMap().get("span_id")).isNull();
    assertThat(events.get(0).getMDCPropertyMap().get("trace_flags")).isNull();
    assertThat(events.get(0).getMDCPropertyMap().get("baggage.baggage_key"))
        .isEqualTo(expectBaggage() ? "baggage_value" : null);

    assertThat(events.get(1).getMessage()).isEqualTo("log message 2");
    assertThat(events.get(1).getMDCPropertyMap().get("trace_id")).isNull();
    assertThat(events.get(1).getMDCPropertyMap().get("span_id")).isNull();
    assertThat(events.get(1).getMDCPropertyMap().get("trace_flags")).isNull();
    assertThat(events.get(1).getMDCPropertyMap().get("baggage.baggage_key"))
        .isEqualTo(expectBaggage() ? "baggage_value" : null);
  }

  void runWithBaggage(Baggage baggage, Runnable runnable) {
    try (Scope unusedScope = baggage.makeCurrent()) {
      runnable.run();
    }
  }

  protected boolean expectBaggage() {
    return false;
  }
}
