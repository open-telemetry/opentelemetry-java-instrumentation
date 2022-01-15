/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.test.annotation.SayTracedHello

/**
 * This test verifies that Otel supports various 3rd-party trace annotations
 */
class TraceProvidersTest extends AgentInstrumentationSpecification {

  def "should support #provider"(String provider) {
    setup:
    new SayTracedHello()."${provider.toLowerCase()}"()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "SayTracedHello.${provider.toLowerCase()}"
          hasNoParent()
          attributes {
            "$SemanticAttributes.CODE_NAMESPACE" SayTracedHello.name
            "$SemanticAttributes.CODE_FUNCTION" "${provider.toLowerCase()}"
            "providerAttr" provider
          }
        }
      }
    }

    where:
    provider << ["AppOptics", "Datadog", "Dropwizard", "KamonOld", "KamonNew", "NewRelic", "SignalFx", "Sleuth", "Tracelytics"]
  }

}
