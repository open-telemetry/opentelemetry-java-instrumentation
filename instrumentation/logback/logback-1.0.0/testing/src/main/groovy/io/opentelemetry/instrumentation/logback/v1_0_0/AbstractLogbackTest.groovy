/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.logback.v1_0_0

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.opentelemetry.auto.test.InstrumentationSpecification
import io.opentelemetry.auto.test.utils.TraceUtils
import io.opentelemetry.trace.Span
import io.opentelemetry.trace.TracingContextUtils
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
      span1 = TracingContextUtils.currentSpan
      logger.info("log message 1")
    }

    logger.info("log message 2")

    Span span2
    TraceUtils.runUnderTrace("test 2") {
      span2 = TracingContextUtils.currentSpan
      logger.info("log message 3")
    }

    def events = listAppender.list

    then:
    events.size() == 3
    events[0].message == "log message 1"
    events[0].getMDCPropertyMap().get("traceId") == span1.context.traceIdAsHexString
    events[0].getMDCPropertyMap().get("spanId") == span1.context.spanIdAsHexString
    events[0].getMDCPropertyMap().get("sampled") == "true"

    events[1].message == "log message 2"
    events[1].getMDCPropertyMap().get("traceId") == null
    events[1].getMDCPropertyMap().get("spanId") == null
    events[1].getMDCPropertyMap().get("sampled") == null

    events[2].message == "log message 3"
    events[2].getMDCPropertyMap().get("traceId") == span2.context.traceIdAsHexString
    events[2].getMDCPropertyMap().get("spanId") == span2.context.spanIdAsHexString
    events[2].getMDCPropertyMap().get("sampled") == "true"
  }
}
