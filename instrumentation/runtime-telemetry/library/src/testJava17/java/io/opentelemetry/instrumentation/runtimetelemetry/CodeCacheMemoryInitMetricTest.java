/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.ATTR_CODE_CACHE;
import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.BYTES;
import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.METRIC_DESCRIPTION_MEMORY_INIT;
import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.METRIC_NAME_MEMORY_INIT;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CodeCacheMemoryInitMetricTest {

  @RegisterExtension JfrExtension jfrExtension = new JfrExtension("jvm.memory.init");

  @Test
  void shouldHaveMemoryInitMetrics() {
    jfrExtension.waitAndAssertMetrics(
        metric ->
            metric
                .hasName(METRIC_NAME_MEMORY_INIT)
                .hasDescription(METRIC_DESCRIPTION_MEMORY_INIT)
                .hasUnit(BYTES)
                .hasLongSumSatisfying(
                    sum ->
                        sum.containsPointsSatisfying(
                            point ->
                                point
                                    .hasAttributes(ATTR_CODE_CACHE)
                                    .hasValueSatisfying(v -> v.isPositive()))));
  }
}
