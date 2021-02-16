/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oshi

import static java.util.concurrent.TimeUnit.SECONDS

import com.google.common.base.Stopwatch
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import oshi.PlatformEnum
import oshi.SystemInfo

class OshiTest extends AgentInstrumentationSpecification {

  def "test system metrics is enabled"() {
    setup:
    PlatformEnum platform = SystemInfo.getCurrentPlatformEnum()

    expect:
    platform != null
    // TODO (trask) is this the instrumentation library name we want?
    findMetric("io.opentelemetry.javaagent.shaded.instrumentation.oshi", "system.disk.io") != null
    findMetric("io.opentelemetry.javaagent.shaded.instrumentation.oshi", "system.disk.operations") != null
    findMetric("io.opentelemetry.javaagent.shaded.instrumentation.oshi", "system.memory.usage") != null
    findMetric("io.opentelemetry.javaagent.shaded.instrumentation.oshi", "system.memory.utilization") != null
    findMetric("io.opentelemetry.javaagent.shaded.instrumentation.oshi", "system.network.errors") != null
    findMetric("io.opentelemetry.javaagent.shaded.instrumentation.oshi", "system.network.io") != null
    findMetric("io.opentelemetry.javaagent.shaded.instrumentation.oshi", "system.network.packets") != null
  }

  def findMetric(instrumentationName, metricName) {
    Stopwatch stopwatch = Stopwatch.createStarted()
    while (stopwatch.elapsed(SECONDS) < 10) {
      for (def metric : metrics) {
        if (metric.instrumentationLibraryInfo.name == instrumentationName && metric.name == metricName) {
          return metric
        }
      }
    }
  }
}
