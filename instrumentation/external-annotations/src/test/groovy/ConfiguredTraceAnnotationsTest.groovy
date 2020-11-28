/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.api.config.Config.normalizePropertyName
import static io.opentelemetry.javaagent.instrumentation.extannotations.TraceAnnotationsInstrumentationModule.DEFAULT_ANNOTATIONS

import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.instrumentation.test.utils.ConfigUtils
import io.opentelemetry.javaagent.instrumentation.extannotations.TraceAnnotationsInstrumentationModule
import io.opentelemetry.test.annotation.SayTracedHello
import java.util.concurrent.Callable

class ConfiguredTraceAnnotationsTest extends AgentTestRunner {
  static final PREVIOUS_CONFIG = ConfigUtils.updateConfigAndResetInstrumentation {
    it.setProperty("otel.instrumentation.external-annotations.include",
      "package.Class\$Name;${OuterClass.InterestingMethod.name}")
  }

  def specCleanup() {
    ConfigUtils.setConfig(PREVIOUS_CONFIG)
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
    def previousConfig = ConfigUtils.updateConfig {
      if (value) {
        it.setProperty("otel.instrumentation.external-annotations.include", value)
      } else {
        // need to remove normalized property name (which has '-' replaced by '.')
        it.remove(normalizePropertyName("otel.instrumentation.external-annotations.include"))
      }
    }

    expect:
    new TraceAnnotationsInstrumentationModule.AnnotatedMethodsInstrumentation().additionalTraceAnnotations == expected.toSet()

    cleanup:
    ConfigUtils.setConfig(previousConfig)

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

  static class AnnotationTracedCallable implements Callable<String> {
    @OuterClass.InterestingMethod
    @Override
    String call() throws Exception {
      return "Hello!"
    }
  }
}
