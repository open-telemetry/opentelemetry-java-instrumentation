/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.SAMPLED
import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.SPAN_ID
import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.TRACE_ID

import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.log4j.v2_13_2.ListAppender
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.TraceUtils
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
    events[0].message.formattedMessage == "log message 1"
    events[0].getContextData().getValue(TRACE_ID) == null
    events[0].getContextData().getValue(SPAN_ID) == null
    events[0].getContextData().getValue(SAMPLED) == null

    events[1].message.formattedMessage == "log message 2"
    events[1].getContextData().getValue(TRACE_ID) == null
    events[1].getContextData().getValue(SPAN_ID) == null
    events[1].getContextData().getValue(SAMPLED) == null
  }

  def "ids when span"() {
    given:
    def logger = LogManager.getLogger("TestLogger")

    when:
    Span span1 = TraceUtils.runUnderTrace("test") {
      logger.info("log message 1")
      Span.current()
    }

    logger.info("log message 2")

    Span span2 = TraceUtils.runUnderTrace("test 2") {
      logger.info("log message 3")
      Span.current()
    }

    def events = ListAppender.get().getEvents()

    then:
    events.size() == 3
    events[0].message.formattedMessage == "log message 1"
    events[0].getContextData().getValue(TRACE_ID) == span1.spanContext.traceIdAsHexString
    events[0].getContextData().getValue(SPAN_ID) == span1.spanContext.spanIdAsHexString
    events[0].getContextData().getValue(SAMPLED) == "true"

    events[1].message.formattedMessage == "log message 2"
    events[1].getContextData().getValue(TRACE_ID) == null
    events[1].getContextData().getValue(SPAN_ID) == null
    events[1].getContextData().getValue(SAMPLED) == null

    events[2].message.formattedMessage == "log message 3"
    events[2].getContextData().getValue(TRACE_ID) == span2.spanContext.traceIdAsHexString
    events[2].getContextData().getValue(SPAN_ID) == span2.spanContext.spanIdAsHexString
    events[2].getContextData().getValue(SAMPLED) == "true"
  }
}
