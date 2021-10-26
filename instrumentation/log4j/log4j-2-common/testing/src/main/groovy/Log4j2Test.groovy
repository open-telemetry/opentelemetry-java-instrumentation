/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.log4j.v2_13_2.ListAppender
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import org.apache.logging.log4j.LogManager

abstract class Log4j2Test extends InstrumentationSpecification {
  def cleanup() {
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
    Span span1 = runWithSpan("test") {
      logger.info("log message 1")
      Span.current()
    }

    logger.info("log message 2")

    Span span2 = runWithSpan("test 2") {
      logger.info("log message 3")
      Span.current()
    }

    def events = ListAppender.get().getEvents()

    then:
    events.size() == 3
    events[0].message == "log message 1"
    events[0].contextData["trace_id"] == span1.spanContext.traceId
    events[0].contextData["span_id"] == span1.spanContext.spanId
    events[0].contextData["trace_flags"] == "01"

    events[1].message == "log message 2"
    events[1].contextData["trace_id"] == null
    events[1].contextData["span_id"] == null
    events[1].contextData["trace_flags"] == null

    events[2].message == "log message 3"
    events[2].contextData["trace_id"] == span2.spanContext.traceId
    events[2].contextData["span_id"] == span2.spanContext.spanId
    events[2].contextData["trace_flags"] == "01"
  }
}
