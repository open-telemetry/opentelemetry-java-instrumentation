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

package io.opentelemetry.auto.test.log.events

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.instrumentation.library.api.config.Config
import spock.lang.Unroll

import static io.opentelemetry.auto.test.utils.ConfigUtils.withConfigOverride
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace

/**
 * This class represents the standard test cases that new logging library integrations MUST
 * satisfy in order to support log events.
 */
@Unroll
abstract class LogEventsTestBase extends AgentTestRunner {

  abstract Object createLogger(String name)

  String warn() {
    return "warn"
  }

  String error() {
    return "error"
  }

  def "capture #testMethod (#capture)"() {
    setup:
    runUnderTrace("test") {
      def logger = createLogger("abc")
      withConfigOverride(Config.EXPERIMENTAL_LOG_CAPTURE_THRESHOLD, "WARN") {
        logger."$testMethod"("xyz")
      }
    }

    expect:
    assertTraces(1) {
      trace(0, capture ? 2 : 1) {
        span(0) {
          operationName "test"
        }
        if (capture) {
          span(1) {
            operationName "log.message"
            attributes {
              "message" "xyz"
              "level" testMethod.toUpperCase()
              "loggerName" "abc"
            }
          }
        }
      }
    }

    where:
    testMethod | capture
    "info"     | false
    warn()     | true
    error()    | true
  }

  def "capture #testMethod (#capture) as span when no current span"() {
    when:
    def logger = createLogger("abc")
    withConfigOverride(Config.EXPERIMENTAL_LOG_CAPTURE_THRESHOLD, "WARN") {
      logger."$testMethod"("xyz")
    }

    then:
    if (capture) {
      assertTraces(1) {
        trace(0, 1) {
          span(0) {
            operationName "log.message"
            attributes {
              "message" "xyz"
              "level" testMethod.toUpperCase()
              "loggerName" "abc"
            }
          }
        }
      }
    } else {
      Thread.sleep(500) // sleep a bit just to make sure no span is captured
      assertTraces(0) {
      }
    }

    where:
    testMethod | capture
    "info"     | false
    warn()     | true
    error()    | true
  }
}
