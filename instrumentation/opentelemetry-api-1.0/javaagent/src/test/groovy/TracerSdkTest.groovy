/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.TracerSdkProvider

class TracerSdkTest extends AgentTestRunner {

  def "direct access to sdk should not fail"() {
    when:
    def provider = OpenTelemetrySdk.getGlobalTracerManagement()

    then:
    provider instanceof TracerSdkProvider
  }
}
