/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.ATTR_COMPRESSED_CLASS_SPACE;
import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.BYTES;
import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.METRIC_DESCRIPTION_MEMORY_LIMIT;
import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.METRIC_NAME_MEMORY_LIMIT;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.runtimetelemetry.internal.JfrFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MetaspaceMemoryLimitMetricTest {

  @RegisterExtension
  JfrExtension jfrExtension =
      new JfrExtension(
          jfrConfig -> {
            jfrConfig.disableAllFeatures();
            jfrConfig.enableFeature(JfrFeature.MEMORY_POOL_METRICS);
          });

  @Test
  void shouldHaveMemoryLimitMetrics() {
    System.gc();
    jfrExtension.waitAndAssertMetrics(
        metric ->
            metric
                .hasName(METRIC_NAME_MEMORY_LIMIT)
                .hasUnit(BYTES)
                .hasDescription(METRIC_DESCRIPTION_MEMORY_LIMIT)
                .satisfies(
                    data ->
                        assertThat(data.getLongSumData().getPoints())
                            .anyMatch(p -> p.getAttributes().equals(ATTR_COMPRESSED_CLASS_SPACE))));
  }
}
