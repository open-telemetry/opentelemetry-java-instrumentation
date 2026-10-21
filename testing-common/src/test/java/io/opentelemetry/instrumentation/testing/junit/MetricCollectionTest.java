/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.util.MetricAssertions.longGauge;
import static io.opentelemetry.instrumentation.testing.util.MetricAssertions.longSum;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MetricCollectionTest {

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void combinesDeltaCollectionsAndStartsNewWindowAfterClear() {
    LongCounter counter =
        testing
            .getOpenTelemetry()
            .getMeter("test.metric.collection")
            .counterBuilder("requests")
            .build();
    Attributes attributes = Attributes.of(stringKey("operation"), "read");
    counter.add(2, attributes);
    testing.metrics();
    testing.metrics();
    counter.add(1, attributes);

    testing.waitAndAssertMetricsExactly(
        "test.metric.collection",
        longSum(
            metric -> metric.hasName("requests"),
            point -> point.hasValue(3).hasAttributes(attributes)));

    testing.clearData();
    counter.add(4, attributes);
    testing.waitAndAssertMetricsExactly(
        "test.metric.collection",
        longSum(
            metric -> metric.hasName("requests"),
            point -> point.hasValue(4).hasAttributes(attributes)));
  }

  @Test
  void retriesAgainstTheLatestObservableGaugeSnapshot() {
    AtomicBoolean updated = new AtomicBoolean();
    testing
        .getOpenTelemetry()
        .getMeter("test.metric.collection.gauge")
        .gaugeBuilder("current")
        .ofLongs()
        .buildWithCallback(measurement -> measurement.record(updated.get() ? 1 : 0));

    testing.waitAndAssertMetricsExactly(
        "test.metric.collection.gauge",
        metric -> {
          updated.set(true);
          longGauge(gauge -> gauge.hasName("current"), point -> point.hasValue(1)).accept(metric);
        });
  }
}
