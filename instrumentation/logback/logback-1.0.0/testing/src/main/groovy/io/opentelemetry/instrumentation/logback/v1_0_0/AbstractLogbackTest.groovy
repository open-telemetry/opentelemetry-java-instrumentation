/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.v1_0_0

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.TraceUtils
import io.opentelemetry.api.trace.Span
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
    logger.info("log message 1")
    logger.info("log message 2")

    def events = listAppender.list

    then:
    events.size() == 2
    events[0].message == "log message 1"
    events[0].getMDCPropertyMap().get("traceId") == null
    events[0].getMDCPropertyMap().get("spanId") == null
    events[0].getMDCPropertyMap().get("traceFlags") == null

    events[1].message == "log message 2"
    events[1].getMDCPropertyMap().get("traceId") == null
    events[1].getMDCPropertyMap().get("spanId") == null
    events[1].getMDCPropertyMap().get("traceFlags") == null
  }

  def "ids when span"() {
    when:
    Span span1
    TraceUtils.runUnderTrace("test") {
      span1 = Span.current()
      logger.info("log message 1")
    }

    logger.info("log message 2")

    Span span2
    TraceUtils.runUnderTrace("test 2") {
      span2 = Span.current()
      logger.info("log message 3")
    }

    def events = listAppender.list

    then:
    events.size() == 3
    events[0].message == "log message 1"
    events[0].getMDCPropertyMap().get("traceId") == span1.spanContext.traceIdAsHexString
    events[0].getMDCPropertyMap().get("spanId") == span1.spanContext.spanIdAsHexString
    events[0].getMDCPropertyMap().get("sampled") == "true"

    events[1].message == "log message 2"
    events[1].getMDCPropertyMap().get("traceId") == null
    events[1].getMDCPropertyMap().get("spanId") == null
    events[1].getMDCPropertyMap().get("sampled") == null

    events[2].message == "log message 3"
    events[2].getMDCPropertyMap().get("traceId") == span2.spanContext.traceIdAsHexString
    events[2].getMDCPropertyMap().get("spanId") == span2.spanContext.spanIdAsHexString
    events[2].getMDCPropertyMap().get("sampled") == "true"
  }
}
