/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.TraceUtils
import org.apache.log4j.LogManager

class Log4j1MdcTest extends AgentInstrumentationSpecification {
  def cleanup() {
    ListAppender.clearEvents()
  }

  def "no ids when no span"() {
    given:
    def logger = LogManager.getLogger('TestLogger')

    when:
    logger.info("log message 1")
    logger.info("log message 2")

    then:
    def events = ListAppender.events

    events.size() == 2
    events[0].message == "log message 1"
    events[0].getMDC("trace_id") == null
    events[0].getMDC("span_id") == null
    events[0].getMDC("trace_flags") == null

    events[1].message == "log message 2"
    events[1].getMDC("trace_id") == null
    events[1].getMDC("span_id") == null
    events[1].getMDC("trace_flags") == null
  }

  def "ids when span"() {
    given:
    def logger = LogManager.getLogger('TestLogger')

    when:
    def span1 = TraceUtils.runUnderTrace("test") {
      logger.info("log message 1")
      Span.current()
    }

    logger.info("log message 2")

    def span2 = TraceUtils.runUnderTrace("test 2") {
      logger.info("log message 3")
      Span.current()
    }

    then:
    def events = ListAppender.events

    events.size() == 3
    events[0].message == "log message 1"
    events[0].getMDC("trace_id") == span1.spanContext.traceId
    events[0].getMDC("span_id") == span1.spanContext.spanId
    events[0].getMDC("trace_flags") == "01"

    events[1].message == "log message 2"
    events[1].getMDC("trace_id") == null
    events[1].getMDC("span_id") == null
    events[1].getMDC("trace_flags") == null

    events[2].message == "log message 3"
    // this explicit getMDCCopy() call here is to make sure that whole instrumentation is tested
    events[2].getMDCCopy()
    events[2].getMDC("trace_id") == span2.spanContext.traceId
    events[2].getMDC("span_id") == span2.spanContext.spanId
    events[2].getMDC("trace_flags") == "01"
  }
}
