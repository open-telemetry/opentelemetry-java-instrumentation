/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.runtimetelemetryjfr

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import spock.util.concurrent.PollingConditions

class JfrRuntimeMetricsTest extends AgentInstrumentationSpecification {

  def "test JFR runtime metrics is enabled"() {
    when:
    def conditions = new PollingConditions(timeout: 10, initialDelay: 1.5, factor: 1.25)
    System.gc()

    then:
    conditions.eventually {
      assert getMetrics().any { it.name == "process.runtime.jvm.cpu.longlock" }
      assert getMetrics().any { it.name == "process.runtime.jvm.cpu.limit" }
      assert getMetrics().any { it.name == "process.runtime.jvm.cpu.context_switch" }

// TODO: need actions to gurantee these metrics are generated
//      assert getMetrics().any { it.name == "process.runtime.jvm.memory.allocation" }
//      assert getMetrics().any { it.name == "process.runtime.jvm.network.io" }
//      assert getMetrics().any { it.name == "process.runtime.jvm.network.time" }
    }
  }
}
