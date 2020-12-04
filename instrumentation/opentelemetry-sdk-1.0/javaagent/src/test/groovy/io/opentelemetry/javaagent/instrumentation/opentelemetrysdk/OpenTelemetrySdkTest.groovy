/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetrysdk

import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.sdk.OpenTelemetrySdk

class OpenTelemetrySdkTest extends AgentTestRunner {

  def "direct access to sdk should not fail"() {
    when:
    def provider = OpenTelemetrySdk.getGlobalTracerManagement()

    then:
    provider instanceof NoopTracerManagement
  }
}
