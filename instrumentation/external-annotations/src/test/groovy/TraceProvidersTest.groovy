/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
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
          operationName "SayTracedHello.${provider.toLowerCase()}"
          parent()
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
