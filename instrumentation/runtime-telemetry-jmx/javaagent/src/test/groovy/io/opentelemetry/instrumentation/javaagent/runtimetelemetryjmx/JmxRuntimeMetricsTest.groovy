/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.runtimetelemetryjmx

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import spock.util.concurrent.PollingConditions

class JmxRuntimeMetricsTest extends AgentInstrumentationSpecification {

  def "test runtime metrics is enabled"() {
    when:
    def conditions = new PollingConditions(timeout: 10, initialDelay: 1.5, factor: 1.25)
    // Force a gc to ensure gc metrics
    System.gc()

    then:
    conditions.eventually {
      assert getMetrics().any { it.name == "process.runtime.jvm.classes.loaded" }
      assert getMetrics().any { it.name == "process.runtime.jvm.classes.unloaded" }
      assert getMetrics().any { it.name == "process.runtime.jvm.classes.current_loaded" }
      assert getMetrics().any { it.name == "process.runtime.jvm.system.cpu.load_1m" }
      assert getMetrics().any { it.name == "process.runtime.jvm.system.cpu.utilization" }
      assert getMetrics().any { it.name == "process.runtime.jvm.cpu.utilization" }
      assert getMetrics().any { it.name == "process.runtime.jvm.gc.duration" }
      assert getMetrics().any { it.name == "process.runtime.jvm.memory.init" }
      assert getMetrics().any { it.name == "process.runtime.jvm.memory.usage" }
      assert getMetrics().any { it.name == "process.runtime.jvm.memory.committed" }
      assert getMetrics().any { it.name == "process.runtime.jvm.memory.limit" }
      assert getMetrics().any { it.name == "process.runtime.jvm.memory.usage_after_last_gc" }
      assert getMetrics().any { it.name == "process.runtime.jvm.threads.count" }
      assert getMetrics().any { it.name == "process.runtime.jvm.buffer.limit" }
      assert getMetrics().any { it.name == "process.runtime.jvm.buffer.count" }
      assert getMetrics().any { it.name == "process.runtime.jvm.buffer.usage" }
    }
  }
}
