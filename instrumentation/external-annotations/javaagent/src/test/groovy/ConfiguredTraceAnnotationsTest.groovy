/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.test.annotation.SayTracedHello
import java.util.concurrent.Callable

class ConfiguredTraceAnnotationsTest extends AgentTestRunner {

  def "method with disabled NewRelic annotation should be ignored"() {
    setup:
    SayTracedHello.fromCallableWhenDisabled()

    expect:
    TEST_WRITER.traces == []
  }

  def "method with custom annotation should be traced"() {
    expect:
    new AnnotationTracedCallable().call() == "Hello!"

    and:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "AnnotationTracedCallable.call"
          attributes {
          }
        }
      }
    }
  }

  static class AnnotationTracedCallable implements Callable<String> {
    @OuterClass.InterestingMethod
    @Override
    String call() throws Exception {
      return "Hello!"
    }
  }
}
