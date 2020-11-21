/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oshi

import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.data.MetricData
import oshi.PlatformEnum
import oshi.SystemInfo

class OshiTest extends AgentTestRunner {

  def "test system metrics is enabled"() {
    setup:
    PlatformEnum platform = SystemInfo.getCurrentPlatformEnum()

    expect:
    platform != null
    Collection<MetricData> metrics = OpenTelemetrySdk.getGlobalMeterProvider().getMetricProducer().collectAllMetrics()
    metrics.size() == 7
  }
}
