/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.instrumentation.test.utils.TraceUtils
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
    events[0].getMDC("traceId") == null
    events[0].getMDC("spanId") == null
    events[0].getMDC("sampled") == null

    events[1].message == "log message 2"
    events[1].getMDC("traceId") == null
    events[1].getMDC("spanId") == null
    events[1].getMDC("sampled") == null
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
    events[0].getMDC("traceId") == span1.spanContext.traceIdAsHexString
    events[0].getMDC("spanId") == span1.spanContext.spanIdAsHexString
    events[0].getMDC("sampled") == "true"

    events[1].message == "log message 2"
    events[1].getMDC("traceId") == null
    events[1].getMDC("spanId") == null
    events[1].getMDC("sampled") == null

    events[2].message == "log message 3"
    // this explicit getMDCCopy() call here is to make sure that whole instrumentation is tested
    events[2].getMDCCopy()
    events[2].getMDC("traceId") == span2.spanContext.traceIdAsHexString
    events[2].getMDC("spanId") == span2.spanContext.spanIdAsHexString
    events[2].getMDC("sampled") == "true"
  }
}
