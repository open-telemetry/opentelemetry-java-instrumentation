/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.contextdata;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class Log4j2Test {
  public abstract InstrumentationExtension getInstrumentationExtension();

  @BeforeEach
  void setUp() {
    ListAppender.get().clearEvents();
  }

  boolean expectBaggage() {
    return false;
  }

  boolean expectLoggingKeys() {
    return false;
  }

  private String getLoggingKey(String key) {
    return expectLoggingKeys() ? key + "_test" : key;
  }

  @Test
  void testNoIdsWhenNoSpan() {
    Logger logger = LogManager.getLogger("TestLogger");

    logger.info("log message 1");
    logger.info("log message 2");

    List<ListAppender.LoggedEvent> events = ListAppender.get().getEvents();

    assertThat(events.size()).isEqualTo(2);

    assertThat(events.get(0).getMessage()).isEqualTo("log message 1");
    assertThat(events.get(0).getContextData().get(getLoggingKey("trace_id"))).isNull();
    assertThat(events.get(0).getContextData().get(getLoggingKey("span_id"))).isNull();
    assertThat(events.get(0).getContextData().get(getLoggingKey("trace_flags"))).isNull();

    assertThat(events.get(1).getMessage()).isEqualTo("log message 2");
    assertThat(events.get(1).getContextData().get(getLoggingKey("trace_id"))).isNull();
    assertThat(events.get(1).getContextData().get(getLoggingKey("span_id"))).isNull();
    assertThat(events.get(1).getContextData().get(getLoggingKey("trace_flags"))).isNull();
  }

  @Test
  void testIdsWhenSpan() {
    Logger logger = LogManager.getLogger("TestLogger");

    Baggage baggage = Baggage.empty().toBuilder().put("baggage_key", "baggage_value").build();
    AtomicReference<Span> spanParent = new AtomicReference<>();
    AtomicReference<Span> spanChild = new AtomicReference<>();
    try (Scope unusedScope = baggage.makeCurrent()) {
      getInstrumentationExtension()
          .runWithSpan(
              "test",
              () -> {
                spanParent.set(Span.current());
                logger.info("log span parent");

                getInstrumentationExtension()
                    .runWithSpan(
                        "test-child",
                        () -> {
                          logger.info("log span child");
                          spanChild.set(Span.current());
                        });
              });
    }

    logger.info("log message 2");

    Span span2 =
        getInstrumentationExtension()
            .runWithSpan(
                "test 2",
                () -> {
                  logger.info("log message 3");
                  return Span.current();
                });

    List<ListAppender.LoggedEvent> events = ListAppender.get().getEvents();

    assertThat(events.size()).isEqualTo(4);

    assertThat(events.get(0).getMessage()).isEqualTo("log span parent");
    assertThat(events.get(0).getContextData().get(getLoggingKey("trace_id")))
        .isEqualTo(spanParent.get().getSpanContext().getTraceId());
    assertThat(events.get(0).getContextData().get(getLoggingKey("span_id")))
        .isEqualTo(spanParent.get().getSpanContext().getSpanId());
    assertThat(events.get(0).getContextData().get(getLoggingKey("trace_flags"))).isEqualTo("01");
    assertThat(events.get(0).getContextData().get("baggage.baggage_key"))
        .isEqualTo((expectBaggage() ? "baggage_value" : null));

    assertThat(events.get(1).getMessage()).isEqualTo("log span child");
    assertThat(events.get(1).getContextData().get(getLoggingKey("trace_id")))
        .isEqualTo(spanChild.get().getSpanContext().getTraceId());
    assertThat(events.get(1).getContextData().get(getLoggingKey("span_id")))
        .isEqualTo(spanChild.get().getSpanContext().getSpanId());
    assertThat(events.get(1).getContextData().get(getLoggingKey("trace_flags"))).isEqualTo("01");
    assertThat(events.get(1).getContextData().get("baggage.baggage_key"))
        .isEqualTo((expectBaggage() ? "baggage_value" : null));

    assertThat(events.get(2).getMessage()).isEqualTo("log message 2");
    assertThat(events.get(2).getContextData().get(getLoggingKey("trace_id"))).isNull();
    assertThat(events.get(2).getContextData().get(getLoggingKey("span_id"))).isNull();
    assertThat(events.get(2).getContextData().get(getLoggingKey("trace_flags"))).isNull();
    assertThat(events.get(2).getContextData().get("baggage.baggage_key")).isNull();

    assertThat(events.get(3).getMessage()).isEqualTo("log message 3");
    assertThat(events.get(3).getContextData().get(getLoggingKey("trace_id")))
        .isEqualTo(span2.getSpanContext().getTraceId());
    assertThat(events.get(3).getContextData().get(getLoggingKey("span_id")))
        .isEqualTo(span2.getSpanContext().getSpanId());
    assertThat(events.get(3).getContextData().get(getLoggingKey("trace_flags"))).isEqualTo("01");
    assertThat(events.get(3).getContextData().get("baggage.baggage_key")).isNull();
  }
}
