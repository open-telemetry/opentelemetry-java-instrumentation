/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.mdc.v1_0

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared

abstract class AbstractLogbackTest extends InstrumentationSpecification {

  private static final Logger logger = LoggerFactory.getLogger("test")

  @Shared
  ListAppender<ILoggingEvent> listAppender

  def setupSpec() {
    ch.qos.logback.classic.Logger logbackLogger = (ch.qos.logback.classic.Logger) logger
    def topLevelListAppender = logbackLogger.getAppender("LIST")
    if (topLevelListAppender != null) {
      // Auto instrumentation test.
      listAppender = topLevelListAppender as ListAppender<ILoggingEvent>
    } else {
      // Library instrumentation test.
      listAppender = (logbackLogger.getAppender("OTEL") as OpenTelemetryAppender)
        .getAppender("LIST") as ListAppender<ILoggingEvent>
    }
  }

  def setup() {
    listAppender.list.clear()
  }

  def "no ids when no span"() {
    when:
    Baggage baggage = Baggage.empty().toBuilder().put("baggage_key", "baggage_value").build()

    runWithBaggage(baggage) {
      AbstractLogbackTest.logger.info("log message 1")
      AbstractLogbackTest.logger.info("log message 2")
    }

    def events = listAppender.list

    then:
    events.size() == 2
    events[0].message == "log message 1"
    events[0].getMDCPropertyMap().get("trace_id") == null
    events[0].getMDCPropertyMap().get("span_id") == null
    events[0].getMDCPropertyMap().get("trace_flags") == null
    events[0].getMDCPropertyMap().get("baggage_key") == (expectBaggage() ? "baggage.baggage_value" : null)

    events[1].message == "log message 2"
    events[1].getMDCPropertyMap().get("trace_id") == null
    events[1].getMDCPropertyMap().get("span_id") == null
    events[1].getMDCPropertyMap().get("trace_flags") == null
    events[1].getMDCPropertyMap().get("baggage_key") == (expectBaggage() ? "baggage.baggage_value" : null)
  }

  def "ids when span"() {
    when:
    Baggage baggage = Baggage.empty().toBuilder().put("baggage_key", "baggage_value").build()

    Span span1 = runWithSpanAndBaggage("test", baggage) {
      AbstractLogbackTest.logger.info("log message 1")
    }

    logger.info("log message 2")

    Span span2 = runWithSpanAndBaggage("test 2", baggage) {
      AbstractLogbackTest.logger.info("log message 3")
    }

    def events = listAppender.list

    then:
    events.size() == 3
    events[0].message == "log message 1"
    events[0].getMDCPropertyMap().get("trace_id") == span1.spanContext.traceId
    events[0].getMDCPropertyMap().get("span_id") == span1.spanContext.spanId
    events[0].getMDCPropertyMap().get("trace_flags") == "01"
    events[0].getMDCPropertyMap().get("baggage_key") == (expectBaggage() ? "baggage.baggage_value" : null)

    events[1].message == "log message 2"
    events[1].getMDCPropertyMap().get("trace_id") == null
    events[1].getMDCPropertyMap().get("span_id") == null
    events[1].getMDCPropertyMap().get("trace_flags") == null
    events[1].getMDCPropertyMap().get("baggage_key") == null

    events[2].message == "log message 3"
    events[2].getMDCPropertyMap().get("trace_id") == span2.spanContext.traceId
    events[2].getMDCPropertyMap().get("span_id") == span2.spanContext.spanId
    events[2].getMDCPropertyMap().get("trace_flags") == "01"
    events[2].getMDCPropertyMap().get("baggage_key") == (expectBaggage() ? "baggage.baggage_value" : null)
  }

  Span runWithSpanAndBaggage(String spanName, Baggage baggage, Closure callback) {
    return runWithSpan(spanName) {
      runWithBaggage(baggage, callback)
      Span.current()
    }
  }

  void runWithBaggage(Baggage baggage, Closure callback) {
    try (var unusedScope = baggage.makeCurrent()) {
      callback.call()
    }
  }

  boolean expectBaggage() {
    return false
  }
}
