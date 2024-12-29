/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17;

import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.ATTR_COMPRESSED_CLASS_SPACE;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.ATTR_METASPACE;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.BYTES;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.METRIC_DESCRIPTION_MEMORY;
import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.METRIC_NAME_MEMORY;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MetaspaceMemoryUsageMetricTest {

  @RegisterExtension
  JfrExtension jfrExtension =
      new JfrExtension(
          builder -> builder.disableAllFeatures().enableFeature(JfrFeature.MEMORY_POOL_METRICS));

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
                .satisfies(
                    data ->
                        assertThat(data.getLongSumData().getPoints())
                            .anyMatch(p -> p.getAttributes().equals(ATTR_METASPACE))
                            .anyMatch(p -> p.getAttributes().equals(ATTR_COMPRESSED_CLASS_SPACE))));
  }
}
