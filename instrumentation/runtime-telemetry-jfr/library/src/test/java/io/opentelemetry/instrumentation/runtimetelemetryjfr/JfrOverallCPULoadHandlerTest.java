/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetryjfr;

import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.UNIT_UTILIZATION;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JfrOverallCPULoadHandlerTest {

  @RegisterExtension
  JfrExtension jfrExtension =
      new JfrExtension(
          builder ->
              builder.disableAllFeatures().enableFeature(JfrFeature.CPU_UTILIZATION_METRICS));

  @Test
  void shouldHaveCPULoadEvents() {
    jfrExtension.waitAndAssertMetrics(
        metric ->
            metric
                .hasName("process.runtime.jvm.cpu.utilization")
                .hasUnit(UNIT_UTILIZATION)
                .hasDescription("Recent CPU utilization for the process")
                .hasDoubleGaugeSatisfying(gauge -> {}),
        metric ->
            metric
                .hasName("process.runtime.jvm.system.cpu.utilization")
                .hasUnit(UNIT_UTILIZATION)
                .hasDescription("Recent CPU utilization for the whole system")
                .hasDoubleGaugeSatisfying(gauge -> {}));
  }
}
