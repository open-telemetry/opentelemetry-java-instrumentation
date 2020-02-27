package io.opentelemetry.auto.test.log.events

import io.opentelemetry.OpenTelemetry
import io.opentelemetry.auto.config.Config
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.trace.Tracer
import spock.lang.Unroll

import static io.opentelemetry.auto.test.utils.ConfigUtils.withConfigOverride

/**
 * This class represents the standard test cases that new logging library integrations MUST
 * satisfy in order to support log events.
 */
@Unroll
abstract class LogEventsTestBase extends AgentTestRunner {

  final Tracer tracer = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.test")

  abstract Object createLogger(String name)

  String warn() {
    return "warn"
  }

  String error() {
    return "error"
  }

  def "capture #testMethod (#capture)"() {
    setup:
    def parentSpan = tracer.spanBuilder("test").startSpan()
    def parentScope = tracer.withSpan(parentSpan)

    def logger = createLogger("abc")
    withConfigOverride(Config.LOGS_EVENTS_THRESHOLD, "WARN") {
      logger."$testMethod"("xyz")
    }

    parentSpan.end()
    parentScope.close()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "test"
          if (capture) {
            event(0) {
              eventName "xyz"
              attributes {
                "level" testMethod.toUpperCase()
                "loggerName" "abc"
              }
            }
          }
          tags {
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
}
