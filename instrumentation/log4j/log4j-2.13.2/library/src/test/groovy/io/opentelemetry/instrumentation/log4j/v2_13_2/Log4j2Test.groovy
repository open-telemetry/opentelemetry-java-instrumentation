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

package io.opentelemetry.instrumentation.log4j.v2_13_2

import io.opentelemetry.auto.test.utils.TraceUtils
import io.opentelemetry.trace.Span
import io.opentelemetry.trace.TracingContextUtils
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import spock.lang.Specification

class Log4j2Test extends Specification {

  private static final Logger logger = LogManager.getLogger("TestLogger")

  def cleanup() {
    ListAppender.get().clearEvents()
  }

  def "no ids when no span"() {
    when:
    logger.info("log message 1")
    logger.info("log message 2")

    def events = ListAppender.get().getEvents()

    then:
    events.size() == 2
    events[0].message.formattedMessage == "log message 1"
    events[0].getContextData().getValue("traceId") == null
    events[0].getContextData().getValue("spanId") == null
    events[0].getContextData().getValue("traceFlags") == null

    events[1].message.formattedMessage == "log message 2"
    events[1].getContextData().getValue("traceId") == null
    events[1].getContextData().getValue("spanId") == null
    events[1].getContextData().getValue("traceFlags") == null
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

    def events = ListAppender.get().getEvents()

    then:
    events.size() == 3
    events[0].message.formattedMessage == "log message 1"
    events[0].getContextData().getValue("traceId") == span1.context.traceIdAsHexString
    events[0].getContextData().getValue("spanId") == span1.context.spanIdAsHexString
    events[0].getContextData().getValue("sampled") == "true"

    events[1].message.formattedMessage == "log message 2"
    events[1].getContextData().getValue("traceId") == null
    events[1].getContextData().getValue("spanId") == null
    events[1].getContextData().getValue("sampled") == null

    events[2].message.formattedMessage == "log message 3"
    events[2].getContextData().getValue("traceId") == span2.context.traceIdAsHexString
    events[2].getContextData().getValue("spanId") == span2.context.spanIdAsHexString
    events[2].getContextData().getValue("sampled") == "true"
  }
}
