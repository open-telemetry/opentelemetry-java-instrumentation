/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.log4j.contextdata.ListAppender
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import org.apache.logging.log4j.LogManager

abstract class Log4j2Test extends InstrumentationSpecification {
  def setup() {
    ListAppender.get().clearEvents()
  }

  def "no ids when no span"() {
    given:
    def logger = LogManager.getLogger("TestLogger")

    when:
    logger.info("log message 1")
    logger.info("log message 2")

    def events = ListAppender.get().getEvents()

    then:
    events.size() == 2
    events[0].message == "log message 1"
    events[0].contextData["trace_id"] == null
    events[0].contextData["span_id"] == null
    events[0].contextData["trace_flags"] == null

    events[1].message == "log message 2"
    events[1].contextData["trace_id"] == null
    events[1].contextData["span_id"] == null
    events[1].contextData["trace_flags"] == null
  }

  def "ids when span"() {
    given:
    def logger = LogManager.getLogger("TestLogger")

    when:
    Baggage baggage = Baggage.empty().toBuilder().put("baggage_key", "baggage_value").build()
    Span spanParent
    Span spanChild
    try (var unusedScope = baggage.makeCurrent()) {
      runWithSpan("test") {
        spanParent = Span.current()
        logger.info("log span parent")

        runWithSpan("test-child") {
          logger.info("log span child")
          spanChild = Span.current()
        }
      }
    }

    logger.info("log message 2")

    Span span2 = runWithSpan("test 2") {
      logger.info("log message 3")
      Span.current()
    }

    def events = ListAppender.get().getEvents()

    then:
    events.size() == 4
    events[0].message == "log span parent"
    events[0].contextData["trace_id"] == spanParent.spanContext.traceId
    events[0].contextData["span_id"] == spanParent.spanContext.spanId
    events[0].contextData["trace_flags"] == "01"
    events[0].contextData["baggage.baggage_key"] == (expectBaggage() ? "baggage_value" : null)

    events[1].message == "log span child"
    events[1].contextData["trace_id"] == spanChild.spanContext.traceId
    events[1].contextData["span_id"] == spanChild.spanContext.spanId
    events[1].contextData["trace_flags"] == "01"
    events[1].contextData["baggage.baggage_key"] == (expectBaggage() ? "baggage_value" : null)

    events[2].message == "log message 2"
    events[2].contextData["trace_id"] == null
    events[2].contextData["span_id"] == null
    events[2].contextData["trace_flags"] == null
    events[2].contextData["baggage.baggage_key"] == null

    events[3].message == "log message 3"
    events[3].contextData["trace_id"] == span2.spanContext.traceId
    events[3].contextData["span_id"] == span2.spanContext.spanId
    events[3].contextData["trace_flags"] == "01"
    events[3].contextData["baggage.baggage_key"] == null
  }

  boolean expectBaggage() {
    return false
  }
}
