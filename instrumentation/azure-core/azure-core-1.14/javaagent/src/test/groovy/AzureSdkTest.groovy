/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.azure.core.http.policy.HttpPolicyProviders
import com.azure.core.util.Context
import com.azure.core.util.tracing.TracerProxy
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification

class AzureSdkTest extends AgentInstrumentationSpecification {

  def "test helper classes injected"() {
    expect:
    TracerProxy.isTracingEnabled()

    def list = new ArrayList()
    HttpPolicyProviders.addAfterRetryPolicies(list)

    list.size() == 1
    list.get(0).getClass().getName() == "io.opentelemetry.javaagent.instrumentation.azurecore.v1_14.shaded" +
      ".com.azure.core.tracing.opentelemetry.OpenTelemetryHttpPolicy"
  }

  def "test span"() {
    when:
    Context context = TracerProxy.start("hello", Context.NONE)
    TracerProxy.end(200, null, context)

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "hello"
          status StatusCode.OK
          attributes {
          }
        }
      }
    }
  }
}
