/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.mdc.v1_2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

public class Log4j1MdcTest {

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

    assertEquals(2, events.size());
    assertEquals("log message 1", events.get(0).getMessage());
    assertNull(events.get(0).getMDC("trace_id"));
    assertNull(events.get(0).getMDC("span_id"));
    assertNull(events.get(0).getMDC("trace_flags"));

    assertEquals("log message 2", events.get(1).getMessage());
    assertNull(events.get(1).getMDC("trace_id"));
    assertNull(events.get(1).getMDC("span_id"));
    assertNull(events.get(1).getMDC("trace_flags"));
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

    assertEquals(3, events.size());
    assertEquals("log message 1", events.get(0).getMessage());
    assertEquals(events.get(0).getMDC("trace_id"), span1.getSpanContext().getTraceId());
    assertEquals(events.get(0).getMDC("span_id"), span1.getSpanContext().getSpanId());
    assertEquals(events.get(0).getMDC("trace_flags"), "01");

    assertEquals("log message 2", events.get(1).getMessage());
    assertNull(events.get(1).getMDC("trace_id"));
    assertNull(events.get(1).getMDC("span_id"));
    assertNull(events.get(1).getMDC("trace_flags"));

    assertEquals("log message 3", events.get(2).getMessage());
    // this explicit getMDCCopy() call here is to make sure that whole instrumentation is tested
    events.get(2).getMDCCopy();
    assertEquals(events.get(2).getMDC("trace_id"), span2.getSpanContext().getTraceId());
    assertEquals(events.get(2).getMDC("span_id"), span2.getSpanContext().getSpanId());
    assertEquals(events.get(2).getMDC("trace_flags"), "01");
  }

  @Test
  void resourceAttributes() {
    logger.info("log message 1");

    List<LoggingEvent> events = ListAppender.getEvents();

    assertEquals(1, events.size());
    assertEquals("log message 1", events.get(0).getMessage());
    assertNull(events.get(0).getMDC("trace_id"));
    assertNull(events.get(0).getMDC("span_id"));
    assertNull(events.get(0).getMDC("trace_flags"));
    assertEquals("unknown_service:java", events.get(0).getMDC("service.name"));
    assertEquals("java", events.get(0).getMDC("telemetry.sdk.language"));
  }
}
