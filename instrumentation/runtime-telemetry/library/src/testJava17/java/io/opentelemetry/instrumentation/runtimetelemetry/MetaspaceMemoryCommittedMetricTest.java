/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.ATTR_COMPRESSED_CLASS_SPACE;
import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.ATTR_METASPACE;
import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.BYTES;
import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.METRIC_DESCRIPTION_COMMITTED;
import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.METRIC_NAME_COMMITTED;

import io.opentelemetry.instrumentation.runtimetelemetry.internal.JfrFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MetaspaceMemoryCommittedMetricTest {

  @RegisterExtension
  JfrExtension jfrExtension =
      new JfrExtension(
          jfrConfig -> {
            jfrConfig.disableAllFeatures();
            jfrConfig.enableFeature(JfrFeature.MEMORY_POOL_METRICS);
          });

  @Test
  void shouldHaveMemoryCommittedMetrics() {
    System.gc();
    jfrExtension.waitAndAssertMetrics(
        metric ->
            metric
                .hasName(METRIC_NAME_COMMITTED)
                .hasUnit(BYTES)
                .hasDescription(METRIC_DESCRIPTION_COMMITTED)
                .hasLongSumSatisfying(
                    sum ->
                        sum.containsPointsSatisfying(
                            point -> point.hasAttributes(ATTR_COMPRESSED_CLASS_SPACE),
                            point -> point.hasAttributes(ATTR_METASPACE))));
  }
}
