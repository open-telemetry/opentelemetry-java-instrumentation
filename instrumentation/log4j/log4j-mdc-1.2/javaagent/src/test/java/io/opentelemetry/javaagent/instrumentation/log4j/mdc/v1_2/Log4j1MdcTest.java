/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.mdc.v1_2;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.List;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class Log4j1MdcTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Logger logger = LogManager.getLogger("TestLogger");

  @BeforeEach
  void cleanup() {
    ListAppender.clearEvents();
  }

  @Test
  void noIdsWhenNoSpan() {
    logger.info("log message 1");
    logger.info("log message 2");

    List<LoggingEvent> events = ListAppender.getEvents();
    assertThat(events)
        .extracting(LoggingEvent::getMessage)
        .containsExactly("log message 1", "log message 2");

    LoggingEvent firstEvent = events.get(0);
    LoggingEvent secondEvent = events.get(1);

    assertThat(firstEvent.getMDC("trace_id")).isNull();
    assertThat(firstEvent.getMDC("span_id")).isNull();
    assertThat(firstEvent.getMDC("trace_flags")).isNull();

    assertThat(secondEvent.getMDC("trace_id")).isNull();
    assertThat(secondEvent.getMDC("span_id")).isNull();
    assertThat(secondEvent.getMDC("trace_flags")).isNull();
  }

  @Test
  void idsWhenSpan() {
    Span span1 =
        testing.runWithSpan(
            "test",
            () -> {
              logger.info("log message 1");
              return Span.current();
            });

    logger.info("log message 2");

    Span span2 =
        testing.runWithSpan(
            "test 2",
            () -> {
              logger.info("log message 3");
              return Span.current();
            });

    List<LoggingEvent> events = ListAppender.getEvents();
    assertThat(events)
        .extracting(LoggingEvent::getMessage)
        .containsExactly("log message 1", "log message 2", "log message 3");

    LoggingEvent firstEvent = events.get(0);
    LoggingEvent secondEvent = events.get(1);
    LoggingEvent thirdEvent = events.get(2);

    assertThat(firstEvent.getMDC("trace_id")).isEqualTo(span1.getSpanContext().getTraceId());
    assertThat(firstEvent.getMDC("span_id")).isEqualTo(span1.getSpanContext().getSpanId());
    assertThat(firstEvent.getMDC("trace_flags"))
        .isEqualTo(span1.getSpanContext().getTraceFlags().asHex());

    assertThat(secondEvent.getMDC("trace_id")).isNull();
    assertThat(secondEvent.getMDC("span_id")).isNull();
    assertThat(secondEvent.getMDC("trace_flags")).isNull();

    // this explicit getMDCCopy() call here is to make sure that whole instrumentation is tested
    thirdEvent.getMDCCopy();
    assertThat(thirdEvent.getMDC("trace_id")).isEqualTo(span2.getSpanContext().getTraceId());
    assertThat(thirdEvent.getMDC("span_id")).isEqualTo(span2.getSpanContext().getSpanId());
    assertThat(thirdEvent.getMDC("trace_flags"))
        .isEqualTo(span2.getSpanContext().getTraceFlags().asHex());
  }

  @Test
  void resourceAttributes() {
    logger.info("log message 1");

    List<LoggingEvent> events = ListAppender.getEvents();
    assertThat(events).extracting(LoggingEvent::getMessage).containsExactly("log message 1");

    LoggingEvent event = events.get(0);
    assertThat(event.getMDC("trace_id")).isNull();
    assertThat(event.getMDC("span_id")).isNull();
    assertThat(event.getMDC("trace_flags")).isNull();
    assertThat(event.getMDC("service.name")).isEqualTo("unknown_service:java");
    assertThat(event.getMDC("telemetry.sdk.language")).isEqualTo("java");
  }
}
