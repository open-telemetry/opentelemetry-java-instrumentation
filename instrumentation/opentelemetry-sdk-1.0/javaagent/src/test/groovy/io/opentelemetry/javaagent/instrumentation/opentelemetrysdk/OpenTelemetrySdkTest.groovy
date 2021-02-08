/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetrysdk

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.sdk.OpenTelemetrySdk

class OpenTelemetrySdkTest extends AgentInstrumentationSpecification {

  def "direct access to sdk should not fail"() {
    when:
    def provider = OpenTelemetrySdk.getGlobalTracerManagement()

    then:
    provider instanceof NoopTracerManagement
  }
}
