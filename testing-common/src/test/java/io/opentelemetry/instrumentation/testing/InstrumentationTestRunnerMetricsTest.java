/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableLongPointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableSumData;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InstrumentationTestRunnerMetricsTest {

  @BeforeEach
  void setAwaitilityDefaults() {
    Awaitility.setDefaultPollDelay(Duration.ZERO);
    Awaitility.setDefaultPollInterval(Duration.ofMillis(1));
    Awaitility.setDefaultTimeout(Duration.ofMillis(20));
  }

  @AfterEach
  void resetAwaitilityDefaults() {
    Awaitility.reset();
  }

  @Test
  void exactAssertionsMatchDistinctMetricIdentitiesOnce() {
    MetricData first =
        metric(Resource.create(Attributes.of(stringKey("resource"), "first")), "requests", "a");
    MetricData second =
        metric(Resource.create(Attributes.of(stringKey("resource"), "second")), "requests", "b");

    runner(asList(singletonList(first), singletonList(second)))
        .waitAndAssertMetricsExactly(
            "test.metric.identities",
            assertion -> assertion.hasName("requests").hasUnit("a"),
            assertion -> assertion.hasName("requests").hasUnit("b"));
  }

  @Test
  void repeatedSnapshotsOfOneIdentityCannotSatisfyTwoExactAssertions() {
    MetricData snapshot = metric(Resource.empty(), "requests", "a");

    assertThatThrownBy(
            () ->
                runner(asList(singletonList(snapshot), singletonList(snapshot)))
                    .waitAndAssertMetricsExactly(
                        "test.metric.identities",
                        assertion -> assertion.hasName("requests"),
                        assertion -> assertion.hasName("requests")))
        .isInstanceOf(AssertionError.class);
  }

  private static MetricData metric(Resource resource, String name, String unit) {
    return ImmutableMetricData.createLongSum(
        resource,
        InstrumentationScopeInfo.create("test.metric.identities"),
        name,
        "",
        unit,
        ImmutableSumData.create(
            true,
            AggregationTemporality.DELTA,
            singletonList(ImmutableLongPointData.create(1, 2, Attributes.empty(), 1))));
  }

  private static InstrumentationTestRunner runner(List<List<MetricData>> collections) {
    return new FixedMetricsTestRunner(collections);
  }

  private static class FixedMetricsTestRunner extends InstrumentationTestRunner {

    private final List<List<MetricData>> collections;

    private FixedMetricsTestRunner(List<List<MetricData>> collections) {
      super(OpenTelemetry.noop());
      this.collections = collections;
    }

    @Override
    public void beforeTestClass() {}

    @Override
    public void afterTestClass() throws IOException {}

    @Override
    public void clearAllExportedData() {}

    @Override
    public OpenTelemetry getOpenTelemetry() {
      return OpenTelemetry.noop();
    }

    @Override
    public List<SpanData> getExportedSpans() {
      return emptyList();
    }

    @Override
    public List<MetricData> getExportedMetrics() {
      return collections.stream().flatMap(List::stream).collect(toList());
    }

    @Override
    public List<List<MetricData>> getExportedMetricCollections() {
      return collections;
    }

    @Override
    public List<LogRecordData> getExportedLogRecords() {
      return emptyList();
    }

    @Override
    public boolean forceFlushCalled() {
      return false;
    }
  }
}
