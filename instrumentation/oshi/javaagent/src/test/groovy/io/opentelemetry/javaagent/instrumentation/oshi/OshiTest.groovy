/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oshi

import io.opentelemetry.instrumentation.test.AgentTestRunner
import oshi.PlatformEnum
import oshi.SystemInfo

class OshiTest extends AgentTestRunner {

  def "test system metrics is enabled"() {
    setup:
    PlatformEnum platform = SystemInfo.getCurrentPlatformEnum()

    expect:
    platform != null
    // TODO(anuraaga): To check metrics for an agent test, we need to actually export and retrieve
    // them.
    // Collection<MetricData> metrics = OpenTelemetrySdk.getGlobalMeterProvider().getMetricProducer().collectAllMetrics()
    // metrics.size() == 7
  }
}
