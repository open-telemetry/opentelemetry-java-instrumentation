/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.ATTR_COMPRESSED_CLASS_SPACE;
import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.BYTES;
import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.METRIC_DESCRIPTION_MEMORY_LIMIT;
import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.METRIC_NAME_MEMORY_LIMIT;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MetaspaceMemoryLimitMetricTest {

  @RegisterExtension JfrExtension jfrExtension = new JfrExtension("jvm.memory.limit");

  @Test
  void shouldHaveMemoryLimitMetrics() {
    System.gc();
    jfrExtension.waitAndAssertMetrics(
        metric ->
            metric
                .hasName(METRIC_NAME_MEMORY_LIMIT)
                .hasUnit(BYTES)
                .hasDescription(METRIC_DESCRIPTION_MEMORY_LIMIT)
                .hasLongSumSatisfying(
                    sum ->
                        sum.containsPointsSatisfying(
                            point -> point.hasAttributes(ATTR_COMPRESSED_CLASS_SPACE))));
  }
}
