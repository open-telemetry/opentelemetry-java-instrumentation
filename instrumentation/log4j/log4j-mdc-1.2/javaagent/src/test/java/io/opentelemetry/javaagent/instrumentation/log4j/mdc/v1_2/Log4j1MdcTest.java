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

    assertThat(events.size()).isEqualTo(2);
    assertThat(events.get(0).getMessage()).isEqualTo("log message 1");
    assertThat(events.get(0).getMDC("trace_id")).isNull();
    assertThat(events.get(0).getMDC("span_id")).isNull();
    assertThat(events.get(0).getMDC("trace_flags")).isNull();

    assertThat(events.get(1).getMessage()).isEqualTo("log message 2");
    assertThat(events.get(1).getMDC("trace_id")).isNull();
    assertThat(events.get(1).getMDC("span_id")).isNull();
    assertThat(events.get(1).getMDC("trace_flags")).isNull();
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

    assertThat(events.size()).isEqualTo(3);
    assertThat(events.get(0).getMessage()).isEqualTo("log message 1");
    assertThat(span1.getSpanContext().getTraceId()).isEqualTo(events.get(0).getMDC("trace_id"));
    assertThat(span1.getSpanContext().getSpanId()).isEqualTo(events.get(0).getMDC("span_id"));
    assertThat("01").isEqualTo(events.get(0).getMDC("trace_flags"));

    assertThat(events.get(1).getMessage()).isEqualTo("log message 2");
    assertThat(events.get(1).getMDC("trace_id")).isNull();
    assertThat(events.get(1).getMDC("span_id")).isNull();
    assertThat(events.get(1).getMDC("trace_flags")).isNull();

    assertThat(events.get(2).getMessage()).isEqualTo("log message 3");
    // this explicit getMDCCopy() call here is to make sure that whole instrumentation is tested
    events.get(2).getMDCCopy();
    assertThat(span2.getSpanContext().getTraceId()).isEqualTo(events.get(2).getMDC("trace_id"));
    assertThat(span2.getSpanContext().getSpanId()).isEqualTo(events.get(2).getMDC("span_id"));
    assertThat("01").isEqualTo(events.get(2).getMDC("trace_flags"));
  }

  @Test
  void resourceAttributes() {
    logger.info("log message 1");

    List<LoggingEvent> events = ListAppender.getEvents();

    assertThat(events.size()).isEqualTo(1);
    assertThat(events.get(0).getMessage()).isEqualTo("log message 1");
    assertThat(events.get(0).getMDC("trace_id")).isNull();
    assertThat(events.get(0).getMDC("span_id")).isNull();
    assertThat(events.get(0).getMDC("trace_flags")).isNull();
    assertThat(events.get(0).getMDC("service.name")).isEqualTo("unknown_service:java");
    assertThat(events.get(0).getMDC("telemetry.sdk.language")).isEqualTo("java");
  }
}
