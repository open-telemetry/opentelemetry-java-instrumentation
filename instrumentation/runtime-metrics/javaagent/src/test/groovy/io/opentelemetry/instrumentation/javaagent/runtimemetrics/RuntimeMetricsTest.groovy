/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.runtimemetrics

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import spock.util.concurrent.PollingConditions

class RuntimeMetricsTest extends AgentInstrumentationSpecification {

  def "test runtime metrics is enabled"() {
    when:
    def conditions = new PollingConditions(timeout: 10, initialDelay: 1.5, factor: 1.25)

    then:
    conditions.eventually {
      assert getMetrics().any { it.name == "process.runtime.jvm.classes.loaded" }
      assert getMetrics().any { it.name == "process.runtime.jvm.classes.unloaded" }
      assert getMetrics().any { it.name == "process.runtime.jvm.classes.current_loaded" }
      assert getMetrics().any { it.name == "runtime.jvm.gc.time" }
      assert getMetrics().any { it.name == "runtime.jvm.gc.count" }
      assert getMetrics().any { it.name == "process.runtime.jvm.memory.init" }
      assert getMetrics().any { it.name == "process.runtime.jvm.memory.usage" }
      assert getMetrics().any { it.name == "process.runtime.jvm.memory.committed" }
      assert getMetrics().any { it.name == "process.runtime.jvm.memory.max" }
      assert getMetrics().any { it.name == "process.runtime.jvm.threads.count" }
    }
  }
}
