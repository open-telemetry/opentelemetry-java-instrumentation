/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.ATTR_COMPRESSED_CLASS_SPACE;
import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.ATTR_METASPACE;
import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.BYTES;
import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.METRIC_DESCRIPTION_MEMORY;
import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.METRIC_NAME_MEMORY;

import io.opentelemetry.instrumentation.runtimetelemetry.internal.JfrFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MetaspaceMemoryUsageMetricTest {

  @RegisterExtension
  JfrExtension jfrExtension =
      new JfrExtension(
          jfrConfig -> {
            jfrConfig.disableAllFeatures();
            jfrConfig.enableFeature(JfrFeature.MEMORY_POOL_METRICS);
          });

  /** This is a basic test for jvm.memory.used. */
  @Test
  void shouldHaveMemoryUsageMetrics() {
    System.gc();

    // Memory spaces in metaspace usage test
    jfrExtension.waitAndAssertMetrics(
        metric ->
            metric
                .hasName(METRIC_NAME_MEMORY)
                .hasUnit(BYTES)
                .hasDescription(METRIC_DESCRIPTION_MEMORY)
                .hasLongSumSatisfying(
                    sum ->
                        sum.containsPointsSatisfying(
                            point -> point.hasAttributes(ATTR_METASPACE),
                            point -> point.hasAttributes(ATTR_COMPRESSED_CLASS_SPACE))));
  }
}
