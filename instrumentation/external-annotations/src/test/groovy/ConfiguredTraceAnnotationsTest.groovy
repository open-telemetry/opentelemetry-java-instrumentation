/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.auto.traceannotation.TraceAnnotationsInstrumentation.DEFAULT_ANNOTATIONS

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.ConfigUtils
import io.opentelemetry.instrumentation.auto.traceannotation.TraceAnnotationsInstrumentation
import io.opentelemetry.test.annotation.SayTracedHello
import java.util.concurrent.Callable

class ConfiguredTraceAnnotationsTest extends AgentTestRunner {

  static {
    ConfigUtils.updateConfig {
      System.setProperty("otel.trace.annotations", "package.Class\$Name;${OuterClass.InterestingMethod.name}")
    }
  }

  def cleanupSpec() {
    ConfigUtils.updateConfig {
      System.clearProperty("otel.trace.annotations")
    }
  }

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

  def "test configuration #value"() {
    setup:
    ConfigUtils.updateConfig {
      if (value) {
        System.properties.setProperty("otel.trace.annotations", value)
      } else {
        System.clearProperty("otel.trace.annotations")
      }
    }

    expect:
    new TraceAnnotationsInstrumentation().additionalTraceAnnotations == expected.toSet()

    cleanup:
    System.clearProperty("otel.trace.annotations")

    where:
    value                               | expected
    null                                | DEFAULT_ANNOTATIONS.toList()
    " "                                 | []
    "some.Invalid[]"                    | []
    "some.package.ClassName "           | ["some.package.ClassName"]
    " some.package.Class\$Name"         | ["some.package.Class\$Name"]
    "  ClassName  "                     | ["ClassName"]
    "ClassName"                         | ["ClassName"]
    "Class\$1;Class\$2;"                | ["Class\$1", "Class\$2"]
    "Duplicate ;Duplicate ;Duplicate; " | ["Duplicate"]
  }

  class AnnotationTracedCallable implements Callable<String> {
    @OuterClass.InterestingMethod
    @Override
    String call() throws Exception {
      return "Hello!"
    }
  }
}
