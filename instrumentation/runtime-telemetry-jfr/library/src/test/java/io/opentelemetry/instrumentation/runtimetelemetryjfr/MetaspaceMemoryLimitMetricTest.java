/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetryjfr;

import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.ATTR_COMPRESSED_CLASS_SPACE;
import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.BYTES;
import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.METRIC_DESCRIPTION_MEMORY_LIMIT;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MetaspaceMemoryLimitMetricTest {

  @RegisterExtension
  JfrExtension jfrExtension =
      new JfrExtension(
          builder -> builder.disableAllFeatures().enableFeature(JfrFeature.MEMORY_POOL_METRICS));

  @Test
  void shouldHaveMemoryLimitMetrics() {
    System.gc();
    jfrExtension.waitAndAssertMetrics(
        metric ->
            metric
                .hasName("process.runtime.jvm.memory.limit")
                .hasUnit(BYTES)
                .hasDescription(METRIC_DESCRIPTION_MEMORY_LIMIT)
                .satisfies(
                    data ->
                        assertThat(data.getLongSumData().getPoints())
                            .anyMatch(p -> p.getAttributes().equals(ATTR_COMPRESSED_CLASS_SPACE))));
  }
}
