/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.SAMPLED
import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.SPAN_ID
import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.TRACE_ID

import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.instrumentation.test.utils.TraceUtils
import io.opentelemetry.api.trace.TracingContextUtils
import org.apache.log4j.LogManager

class Log4j1MdcTest extends AgentTestRunner {
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
    events[0].getMDC(TRACE_ID) == null
    events[0].getMDC(SPAN_ID) == null
    events[0].getMDC(SAMPLED) == null

    events[1].message == "log message 2"
    events[1].getMDC(TRACE_ID) == null
    events[1].getMDC(SPAN_ID) == null
    events[1].getMDC(SAMPLED) == null
  }

  def "ids when span"() {
    given:
    def logger = LogManager.getLogger('TestLogger')

    when:
    def span1 = TraceUtils.runUnderTrace("test") {
      logger.info("log message 1")
      TracingContextUtils.currentSpan
    }

    logger.info("log message 2")

    def span2 = TraceUtils.runUnderTrace("test 2") {
      logger.info("log message 3")
      TracingContextUtils.currentSpan
    }

    then:
    def events = ListAppender.events

    events.size() == 3
    events[0].message == "log message 1"
    events[0].getMDC(TRACE_ID) == span1.context.traceIdAsHexString
    events[0].getMDC(SPAN_ID) == span1.context.spanIdAsHexString
    events[0].getMDC(SAMPLED) == "true"

    events[1].message == "log message 2"
    events[1].getMDC(TRACE_ID) == null
    events[1].getMDC(SPAN_ID) == null
    events[1].getMDC(SAMPLED) == null

    events[2].message == "log message 3"
    // this explicit getMDCCopy() call here is to make sure that whole instrumentation is tested
    events[2].getMDCCopy()
    events[2].getMDC(TRACE_ID) == span2.context.traceIdAsHexString
    events[2].getMDC(SPAN_ID) == span2.context.spanIdAsHexString
    events[2].getMDC(SAMPLED) == "true"
  }
}
