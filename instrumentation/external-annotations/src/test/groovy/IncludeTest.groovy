/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import static io.opentelemetry.instrumentation.api.config.Config.normalizePropertyName
import static io.opentelemetry.javaagent.instrumentation.extannotations.TraceAnnotationsInstrumentationModule.DEFAULT_ANNOTATIONS

import io.opentelemetry.instrumentation.api.config.Config
import io.opentelemetry.javaagent.instrumentation.extannotations.TraceAnnotationsInstrumentationModule
import spock.lang.Specification

class IncludeTest extends Specification {

  def "test configuration #value"() {
    setup:
    Config config
    if (value) {
      config = Config.create([
        (normalizePropertyName("otel.instrumentation.external-annotations.include")): value
      ])
    } else {
      config = Config.DEFAULT
    }

    expect:
    TraceAnnotationsInstrumentationModule.AnnotatedMethodsInstrumentation.configureAdditionalTraceAnnotations(config) == expected.toSet()

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

}
