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

class Log4j1LoggingKeysTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Logger logger = LogManager.getLogger("TestLogger");

  @BeforeEach
  void cleanup() {
    ListAppender.clearEvents();
  }

  @Test
  void customLoggingKeys() {
    Span span =
        testing.runWithSpan(
            "test",
            () -> {
              logger.info("log message");
              return Span.current();
            });

    List<LoggingEvent> events = ListAppender.getEvents();
    assertThat(events).extracting(LoggingEvent::getMessage).containsExactly("log message");

    LoggingEvent event = events.get(0);
    assertThat(event.getMDC("trace_id")).isNull();
    assertThat(event.getMDC("span_id")).isNull();
    assertThat(event.getMDC("trace_flags")).isNull();
    assertThat(event.getMDC("trace_id_test")).isEqualTo(span.getSpanContext().getTraceId());
    assertThat(event.getMDC("span_id_test")).isEqualTo(span.getSpanContext().getSpanId());
    assertThat(event.getMDC("trace_flags_test"))
        .isEqualTo(span.getSpanContext().getTraceFlags().asHex());
  }
}
