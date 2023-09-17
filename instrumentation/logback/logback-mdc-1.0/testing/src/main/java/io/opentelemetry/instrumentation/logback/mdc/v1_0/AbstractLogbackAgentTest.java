/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.mdc.v1_0;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class AbstractLogbackAgentTest extends AbstractLogbackTest {
  @RegisterExtension
  static InstrumentationExtension testing = AgentInstrumentationExtension.create();

  Span runWithSpanAndBaggage(String spanName, Baggage baggage, Runnable runnable) {
    return testing.runWithSpan(
        spanName,
        () -> {
          runWithBaggage(baggage, runnable);
          return Span.current();
        });
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
}
