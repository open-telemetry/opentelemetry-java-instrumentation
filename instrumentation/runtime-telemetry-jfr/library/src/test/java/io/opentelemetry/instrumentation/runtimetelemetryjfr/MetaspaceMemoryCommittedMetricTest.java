/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetryjfr;

import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.ATTR_COMPRESSED_CLASS_SPACE;
import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.ATTR_METASPACE;
import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.BYTES;
import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.METRIC_DESCRIPTION_COMMITTED;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MetaspaceMemoryCommittedMetricTest {

  @RegisterExtension
  JfrExtension jfrExtension =
      new JfrExtension(
          builder -> builder.disableAllFeatures().enableFeature(JfrFeature.MEMORY_POOL_METRICS));

  @Test
  void shouldHaveMemoryCommittedMetrics() {
    System.gc();
    jfrExtension.waitAndAssertMetrics(
        metric ->
            metric
                .hasName("process.runtime.jvm.memory.committed")
                .hasUnit(BYTES)
                .hasDescription(METRIC_DESCRIPTION_COMMITTED)
                .satisfies(
                    data ->
                        assertThat(data.getLongSumData().getPoints())
                            .anyMatch(p -> p.getAttributes().equals(ATTR_COMPRESSED_CLASS_SPACE))
                            .anyMatch(p -> p.getAttributes().equals(ATTR_METASPACE))));
  }
}
