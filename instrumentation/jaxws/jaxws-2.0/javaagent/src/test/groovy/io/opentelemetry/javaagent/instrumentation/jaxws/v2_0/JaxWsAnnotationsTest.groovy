/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v2_0

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes

class JaxWsAnnotationsTest extends AgentInstrumentationSpecification {

  def "Web service providers generate spans"() {
    when:
    new SoapProvider().invoke(null)

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "SoapProvider.invoke"
          attributes {
            "${SemanticAttributes.CODE_NAMESPACE.key}" "io.opentelemetry.javaagent.instrumentation.jaxws.v2_0.SoapProvider"
            "${SemanticAttributes.CODE_FUNCTION.key}" "invoke"
          }
        }
      }
    }
  }
}
