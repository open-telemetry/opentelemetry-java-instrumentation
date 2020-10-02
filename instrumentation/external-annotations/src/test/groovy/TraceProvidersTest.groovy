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
import io.opentelemetry.test.annotation.SayTracedHello

/**
 * This test verifies that Otel supports various 3rd-party trace annotations
 */
class TraceProvidersTest extends AgentTestRunner {

  static {
    ConfigUtils.updateConfig {
      //Don't bother to instrument inner closures of this test class
      System.setProperty("otel.trace.classes.exclude", TraceProvidersTest.name + "*")
    }
  }

  def cleanupSpec() {
    ConfigUtils.updateConfig {
      System.clearProperty("otel.trace.classes.exclude")
    }
  }

  def "should support #provider"(String provider) {
    setup:
    new SayTracedHello()."${provider.toLowerCase()}"()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "SayTracedHello.${provider.toLowerCase()}"
          hasNoParent()
          errored false
          attributes {
            "providerAttr" provider
          }
        }
      }
    }

    where:
    provider << ["AppOptics", "Datadog", "Dropwizard", "KamonOld", "KamonNew", "NewRelic", "SignalFx", "Sleuth", "Tracelytics"]
  }

}
