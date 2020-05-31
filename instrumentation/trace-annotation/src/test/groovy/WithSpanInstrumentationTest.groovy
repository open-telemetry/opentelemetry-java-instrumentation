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
import io.opentelemetry.auto.test.utils.ConfigUtils
import io.opentelemetry.test.annotation.TracedWithSpan

/**
 * This test verifies that auto instrumentation supports {@link io.opentelemetry.contrib.auto.annotations.WithSpan} contrib annotation.
 */
class WithSpanInstrumentationTest extends AgentTestRunner {

  static {
    ConfigUtils.updateConfig {
      System.setProperty("ota.trace.classes.exclude", WithSpanInstrumentationTest.name + "*")
      System.setProperty("ota.trace.methods.exclude", "${TracedWithSpan.name}[ignored]")
    }
  }

  def specCleanup() {
    ConfigUtils.updateConfig {
      System.clearProperty("ota.trace.classes.exclude")
      System.clearProperty("ota.trace.methods.exclude")
    }
  }

  def "should derive automatic name"() {
    setup:
    new TracedWithSpan().otel()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "TracedWithSpan.otel"
          parent()
          errored false
          tags {
            "providerAttr" "Otel"
          }
        }
      }
    }
  }

  def "should take span name from annotation"() {
    setup:
    new TracedWithSpan().namedOtel()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "manualName"
          parent()
          errored false
          tags {
            "providerAttr" "Otel"
          }
        }
      }
    }
  }

  def "should ignore method excluded by trace.methods.exclude configuration"() {
    setup:
    new TracedWithSpan().ignored()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}
  }

}
