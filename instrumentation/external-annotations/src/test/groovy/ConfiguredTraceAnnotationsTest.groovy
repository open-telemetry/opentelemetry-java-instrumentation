/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.api.config.Config
import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.instrumentation.test.utils.ConfigUtils
import io.opentelemetry.javaagent.instrumentation.traceannotation.TraceAnnotationsInstrumentationModule
import io.opentelemetry.test.annotation.SayTracedHello

import java.util.concurrent.Callable

import static io.opentelemetry.javaagent.instrumentation.traceannotation.TraceAnnotationsInstrumentationModule.DEFAULT_ANNOTATIONS

class ConfiguredTraceAnnotationsTest extends AgentTestRunner {
  static final PREVIOUS_CONFIG = ConfigUtils.updateConfigAndResetInstrumentation {
    it.setProperty("otel.trace.annotations", "package.Class\$Name;${OuterClass.InterestingMethod.name}")
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
    // Don't use ConfigUtils since that modifies the config in the agent - here we're just
    // initializing TraceAnnotationsInstrumentationModule directly so this is not using the agent
    // and we need to make sure to modify our (non-shaded) config.
    def previousConfig = Config.get()
    Config.INSTANCE = Config.create(['otel.trace.annotations': value])

    expect:
    new TraceAnnotationsInstrumentationModule.AnnotatedMethodsInstrumentation().additionalTraceAnnotations == expected.toSet()

    cleanup:
    Config.INSTANCE = previousConfig

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
