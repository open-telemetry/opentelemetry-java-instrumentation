/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification

import java.util.concurrent.Callable

class MethodTest extends AgentInstrumentationSpecification {

  static class ConfigTracedCallable implements Callable<String> {
    @Override
    String call() throws Exception {
      return "Hello!"
    }
  }

  def "test configuration based trace"() {
    expect:
    new ConfigTracedCallable().call() == "Hello!"

    and:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "ConfigTracedCallable.call"
          attributes {
          }
        }
      }
    }
  }
}
