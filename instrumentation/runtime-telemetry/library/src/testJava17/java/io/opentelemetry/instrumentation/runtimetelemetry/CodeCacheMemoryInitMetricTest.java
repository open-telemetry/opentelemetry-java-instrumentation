/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.ATTR_CODE_CACHE;
import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.BYTES;
import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.METRIC_DESCRIPTION_MEMORY_INIT;
import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.METRIC_NAME_MEMORY_INIT;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.runtimetelemetry.internal.JfrFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CodeCacheMemoryInitMetricTest {

  @RegisterExtension
  JfrExtension jfrExtension =
      new JfrExtension(
          jfrConfig -> {
            jfrConfig.disableAllFeatures();
            jfrConfig.enableFeature(JfrFeature.MEMORY_POOL_METRICS);
          });

  @Test
  void shouldHaveMemoryInitMetrics() {
    jfrExtension.waitAndAssertMetrics(
        metric ->
            metric
                .hasName(METRIC_NAME_MEMORY_INIT)
                .hasDescription(METRIC_DESCRIPTION_MEMORY_INIT)
                .hasUnit(BYTES)
                .satisfies(
                    data ->
                        assertThat(data.getLongSumData().getPoints())
                            .anyMatch(
                                pointData ->
                                    pointData.getValue() > 0
                                        && pointData.getAttributes().equals(ATTR_CODE_CACHE))));
  }
}
