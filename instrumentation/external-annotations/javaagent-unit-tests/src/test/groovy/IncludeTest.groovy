/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.javaagent.instrumentation.extannotations.TraceAnnotationsInstrumentationModule.DEFAULT_ANNOTATIONS

import io.opentelemetry.instrumentation.api.config.Config
import io.opentelemetry.instrumentation.api.config.ConfigBuilder
import io.opentelemetry.javaagent.instrumentation.extannotations.TraceAnnotationsInstrumentationModule
import spock.lang.Specification
import spock.lang.Unroll

class IncludeTest extends Specification {

  @Unroll
  def "test configuration #value"() {
    setup:
    Config config
    if (value) {
      config = new ConfigBuilder().readProperties([
        "otel.instrumentation.external-annotations.include": value
      ]).build()
    } else {
      config = Config.create([:])
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
