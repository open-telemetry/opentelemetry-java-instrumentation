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

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.TraceUtils
import io.opentelemetry.trace.TracingContextUtils
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
    events[0].getMDC("traceFlags") == null

    events[1].message == "log message 2"
    events[1].getMDC("traceId") == null
    events[1].getMDC("spanId") == null
    events[1].getMDC("traceFlags") == null
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
    events[0].getMDC("traceId") == span1.context.traceIdAsHexString
    events[0].getMDC("spanId") == span1.context.spanIdAsHexString
    events[0].getMDC("sampled") == "true"

    events[1].message == "log message 2"
    events[1].getMDC("traceId") == null
    events[1].getMDC("spanId") == null
    events[1].getMDC("sampled") == null

    events[2].message == "log message 3"
    // this explicit getMDCCopy() call here is to make sure that whole instrumentation is tested
    events[2].getMDCCopy()
    events[2].getMDC("traceId") == span2.context.traceIdAsHexString
    events[2].getMDC("spanId") == span2.context.spanIdAsHexString
    events[2].getMDC("sampled") == "true"
  }
}
