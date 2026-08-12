/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.UNIT_UTILIZATION;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JfrOverallCpuLoadHandlerTest {

  @RegisterExtension
  JfrExtension jfrExtension =
      new JfrExtension("jvm.cpu.recent_utilization", "jvm.system.cpu.utilization");

  @Test
  void shouldHaveCpuLoadEvents() {
    jfrExtension.waitAndAssertMetrics(
        metric ->
            metric
                .hasName("jvm.cpu.recent_utilization")
                .hasUnit(UNIT_UTILIZATION)
                .hasDescription("Recent CPU utilization for the process as reported by the JVM.")
                .hasDoubleGaugeSatisfying(gauge -> {}),
        metric ->
            metric
                .hasName("jvm.system.cpu.utilization")
                .hasUnit(UNIT_UTILIZATION)
                .hasDescription(
                    "Recent CPU utilization for the whole system as reported by the JVM.")
                .hasDoubleGaugeSatisfying(gauge -> {}));
  }
}
