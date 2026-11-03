/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nullable;

/**
 * Accumulates a metric's documented shape across all points and collected snapshots without
 * retaining point values or exemplars.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class CollectedMetric {
  private final String name;
  private final String description;
  private final MetricDataType type;
  private final String unit;
  @Nullable private final Boolean monotonic;
  private final Set<AttributeKey<?>> attributeKeys =
      new TreeSet<>(
          Comparator.comparing((AttributeKey<?> key) -> key.getKey())
              .thenComparing(key -> key.getType().name()));

  public CollectedMetric(MetricData metric) {
    name = metric.getName();
    description = metric.getDescription();
    type = metric.getType();
    unit = metric.getUnit();
    switch (type) {
      case LONG_SUM:
        monotonic = metric.getLongSumData().isMonotonic();
        break;
      case DOUBLE_SUM:
        monotonic = metric.getDoubleSumData().isMonotonic();
        break;
      default:
        monotonic = null;
    }
  }

  public void collect(MetricData metric) {
    metric
        .getData()
        .getPoints()
        .forEach(point -> attributeKeys.addAll(point.getAttributes().asMap().keySet()));
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public MetricDataType getType() {
    return type;
  }

  public String getUnit() {
    return unit;
  }

  @Nullable
  public Boolean isMonotonic() {
    return monotonic;
  }

  public Set<AttributeKey<?>> getAttributeKeys() {
    return Collections.unmodifiableSet(attributeKeys);
  }
}
