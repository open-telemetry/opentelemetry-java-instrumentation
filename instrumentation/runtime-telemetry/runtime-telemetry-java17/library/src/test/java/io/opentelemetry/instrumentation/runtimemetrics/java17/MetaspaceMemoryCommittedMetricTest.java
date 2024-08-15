/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17;

import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.ATTR_COMPRESSED_CLASS_SPACE;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.ATTR_METASPACE;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.BYTES;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.METRIC_DESCRIPTION_COMMITTED;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.METRIC_NAME_COMMITTED;
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
                .hasName(METRIC_NAME_COMMITTED)
                .hasUnit(BYTES)
                .hasDescription(METRIC_DESCRIPTION_COMMITTED)
                .satisfies(
                    data ->
                        assertThat(data.getLongSumData().getPoints())
                            .anyMatch(p -> p.getAttributes().equals(ATTR_COMPRESSED_CLASS_SPACE))
                            .anyMatch(p -> p.getAttributes().equals(ATTR_METASPACE))));
  }
}
