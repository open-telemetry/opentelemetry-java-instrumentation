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
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractLogbackTest {

  @RegisterExtension
  static InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static final Logger logger = LoggerFactory.getLogger("test");

  private static ListAppender<ILoggingEvent> listAppender = new ListAppender<>();

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

  @Test
  void testIdsWhenSpan() {
    Baggage baggage = Baggage.empty().toBuilder().put("baggage_key", "baggage_value").build();

    Span span1 = runWithSpanAndBaggage("test", baggage, () -> logger.info("log message 1"));

    logger.info("log message 2");

    Span span2 = runWithSpanAndBaggage("test 2", baggage, () -> logger.info("log message 3"));

    List<ILoggingEvent> events = listAppender.list;

    assertThat(events.size()).isEqualTo(3);
    assertThat(events.get(0).getMessage()).isEqualTo("log message 1");
    assertThat(events.get(0).getMDCPropertyMap().get("trace_id"))
        .isEqualTo(span1.getSpanContext().getTraceId());
    assertThat(events.get(0).getMDCPropertyMap().get("span_id"))
        .isEqualTo(span1.getSpanContext().getSpanId());
    assertThat(events.get(0).getMDCPropertyMap().get("trace_flags")).isEqualTo("01");
    assertThat(events.get(0).getMDCPropertyMap().get("baggage.baggage_key"))
        .isEqualTo(expectBaggage() ? "baggage_value" : null);

    assertThat(events.get(1).getMessage()).isEqualTo("log message 2");
    assertThat(events.get(1).getMDCPropertyMap().get("trace_id")).isNull();
    assertThat(events.get(1).getMDCPropertyMap().get("span_id")).isNull();
    assertThat(events.get(1).getMDCPropertyMap().get("trace_flags")).isNull();
    assertThat(events.get(1).getMDCPropertyMap().get("baggage.baggage_key")).isNull();

    assertThat(events.get(2).getMessage()).isEqualTo("log message 3");
    assertThat(events.get(2).getMDCPropertyMap().get("trace_id"))
        .isEqualTo(span2.getSpanContext().getTraceId());
    assertThat(events.get(2).getMDCPropertyMap().get("span_id"))
        .isEqualTo(span2.getSpanContext().getSpanId());
    assertThat(events.get(2).getMDCPropertyMap().get("trace_flags")).isEqualTo("01");
    assertThat(events.get(2).getMDCPropertyMap().get("baggage.baggage_key"))
        .isEqualTo(expectBaggage() ? "baggage_value" : null);
  }

  void runWithBaggage(Baggage baggage, Runnable runnable) {
    try (Scope unusedScope = baggage.makeCurrent()) {
      runnable.run();
    }
  }

  Span runWithSpanAndBaggage(String spanName, Baggage baggage, Runnable runnable) {
    return testing.runWithSpan(
        spanName,
        () -> {
          runWithBaggage(baggage, runnable);
          return Span.current();
        });
  }

  boolean expectBaggage() {
    return false;
  }
}
