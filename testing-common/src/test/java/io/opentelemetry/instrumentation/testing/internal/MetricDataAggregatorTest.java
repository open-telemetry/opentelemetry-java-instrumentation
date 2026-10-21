/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.internal;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.util.MetricAssertions.histogram;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.ExponentialHistogramBuckets;
import io.opentelemetry.sdk.metrics.data.ExponentialHistogramPointData;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.SummaryPointData;
import io.opentelemetry.sdk.metrics.data.ValueAtQuantile;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableExponentialHistogramData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableExponentialHistogramPointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableGaugeData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramPointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableLongPointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableSumData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableSummaryData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableSummaryPointData;
import io.opentelemetry.sdk.resources.Resource;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetricDataAggregatorTest {

  private static final Attributes FIRST_ATTRIBUTES = Attributes.of(stringKey("series"), "first");
  private static final Attributes SECOND_ATTRIBUTES = Attributes.of(stringKey("series"), "second");
  private static final Resource RESOURCE =
      Resource.create(Attributes.of(stringKey("service"), "a"));
  private static final InstrumentationScopeInfo SCOPE =
      InstrumentationScopeInfo.builder("test.metrics").setVersion("1").build();

  @Test
  void aggregatesDeltaSumAndPreservesPointAttributeSets() {
    MetricData first = longSum("requests", delta(), longPoint(FIRST_ATTRIBUTES, 2));
    MetricData second =
        longSum(
            "requests", delta(), longPoint(FIRST_ATTRIBUTES, 3), longPoint(SECOND_ATTRIBUTES, 5));

    MetricData result = aggregate(singletonList(first), singletonList(second)).get(0);

    assertThat(result.getLongSumData().getPoints())
        .extracting(LongPointData::getValue)
        .containsExactly(5L, 5L);
    assertThat(result.getLongSumData().getPoints())
        .extracting(LongPointData::getAttributes)
        .containsExactly(FIRST_ATTRIBUTES, SECOND_ATTRIBUTES);
  }

  @Test
  void aggregatesDeltaHistogramCountsAndUnexpectedLaterPointAttributes() {
    MetricData first = histogramMetric("latency", delta(), histogramPoint(FIRST_ATTRIBUTES, 1, 1));
    MetricData second =
        histogramMetric(
            "latency",
            delta(),
            histogramPoint(FIRST_ATTRIBUTES, 2, 2),
            histogramPoint(SECOND_ATTRIBUTES, 1, 3));

    MetricData result = aggregate(singletonList(first), singletonList(second)).get(0);

    assertThat(result.getHistogramData().getPoints())
        .extracting(HistogramPointData::getCount)
        .containsExactly(3L, 1L);
    assertThat(result.getHistogramData().getPoints())
        .extracting(HistogramPointData::getSum)
        .containsExactly(3.0, 3.0);
  }

  @Test
  void exactHistogramAssertionRejectsAnUnexpectedPointFromALaterDeltaExport() {
    MetricData first = histogramMetric("latency", delta(), histogramPoint(FIRST_ATTRIBUTES, 1, 1));
    MetricData second =
        histogramMetric("latency", delta(), histogramPoint(SECOND_ATTRIBUTES, 1, 2));
    MetricData result = aggregate(singletonList(first), singletonList(second)).get(0);

    assertThatThrownBy(
            () ->
                histogram(
                        metric -> metric.hasName("latency"),
                        point -> point.hasAttributes(FIRST_ATTRIBUTES))
                    .accept(assertThat(result)))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void rejectsMismatchedHistogramBoundaries() {
    MetricData first = histogramMetric("latency", delta(), histogramPoint(FIRST_ATTRIBUTES, 1, 1));
    MetricData incompatible =
        ImmutableMetricData.createDoubleHistogram(
            RESOURCE,
            SCOPE,
            "latency",
            "",
            "ms",
            ImmutableHistogramData.create(
                AggregationTemporality.DELTA,
                singletonList(
                    ImmutableHistogramPointData.create(
                        1,
                        2,
                        FIRST_ATTRIBUTES,
                        1,
                        false,
                        0,
                        false,
                        0,
                        singletonList(2.0),
                        asList(0L, 1L)))));

    assertThatThrownBy(() -> aggregate(singletonList(first), singletonList(incompatible)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("histogram boundaries changed");
  }

  @Test
  void rejectsMismatchedHistogramBoundariesAcrossPointAttributeSets() {
    MetricData first = histogramMetric("latency", delta(), histogramPoint(FIRST_ATTRIBUTES, 1, 1));
    MetricData incompatible =
        ImmutableMetricData.createDoubleHistogram(
            RESOURCE,
            SCOPE,
            "latency",
            "",
            "ms",
            ImmutableHistogramData.create(
                AggregationTemporality.DELTA,
                singletonList(
                    ImmutableHistogramPointData.create(
                        1,
                        2,
                        SECOND_ATTRIBUTES,
                        1,
                        false,
                        0,
                        false,
                        0,
                        singletonList(2.0),
                        asList(0L, 1L)))));

    assertThatThrownBy(() -> aggregate(singletonList(first), singletonList(incompatible)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("histogram boundaries changed");
  }

  @Test
  void exponentialHistogramCombinesBucketsAcrossScales() {
    MetricData first =
        exponentialHistogram("size", delta(), exponentialPoint(FIRST_ATTRIBUTES, 2, 1, 1));
    MetricData second =
        exponentialHistogram("size", delta(), exponentialPoint(FIRST_ATTRIBUTES, 1, 2, 2));

    MetricData result = aggregate(singletonList(first), singletonList(second)).get(0);

    ExponentialHistogramPointData point =
        result.getExponentialHistogramData().getPoints().iterator().next();
    assertThat(point.getScale()).isEqualTo(1);
    assertThat(point.getCount()).isEqualTo(3);
    assertThat(point.getSum()).isEqualTo(3);
    assertThat(point.getPositiveBuckets().getTotalCount()).isEqualTo(3);
  }

  @Test
  void latestGaugeSnapshotDropsChangedAndRemovedSeries() {
    MetricData first =
        longGauge("active", longPoint(FIRST_ATTRIBUTES, 1), longPoint(SECOND_ATTRIBUTES, 2));
    MetricData second = longGauge("active", longPoint(FIRST_ATTRIBUTES, 3));
    MetricData removed = longGauge("active");

    MetricData latest = aggregate(singletonList(first), singletonList(second)).get(0);
    assertThat(latest.getLongGaugeData().getPoints())
        .extracting(LongPointData::getAttributes)
        .containsExactly(FIRST_ATTRIBUTES);
    assertThat(aggregate(singletonList(first), singletonList(removed)))
        .singleElement()
        .satisfies(metric -> assertThat(metric.getLongGaugeData().getPoints()).isEmpty());
    assertThat(aggregate(singletonList(first), emptyList())).isEmpty();
  }

  @Test
  void cumulativeSnapshotsUseLatestValuesInsteadOfSumming() {
    MetricData first = longSum("requests", cumulative(), longPoint(FIRST_ATTRIBUTES, 2));
    MetricData second = longSum("requests", cumulative(), longPoint(FIRST_ATTRIBUTES, 5));

    MetricData result = aggregate(singletonList(first), singletonList(second)).get(0);

    assertThat(result.getLongSumData().getPoints())
        .extracting(LongPointData::getValue)
        .containsExactly(5L);
  }

  @Test
  void cumulativeHistogramsUseTheLatestSnapshot() {
    MetricData first =
        histogramMetric("latency", cumulative(), histogramPoint(FIRST_ATTRIBUTES, 2, 2));
    MetricData second =
        histogramMetric("latency", cumulative(), histogramPoint(FIRST_ATTRIBUTES, 5, 5));

    MetricData result = aggregate(singletonList(first), singletonList(second)).get(0);

    assertThat(result.getHistogramData().getPoints())
        .extracting(HistogramPointData::getCount)
        .containsExactly(5L);
    assertThat(aggregate(singletonList(first), emptyList())).isEmpty();
  }

  @Test
  void latestSummarySnapshotDoesNotMergeQuantiles() {
    MetricData first = summary("payload", summaryPoint(2, 2));
    MetricData second = summary("payload", summaryPoint(3, 5));

    MetricData result = aggregate(singletonList(first), singletonList(second)).get(0);

    SummaryPointData point = result.getSummaryData().getPoints().iterator().next();
    assertThat(point.getCount()).isEqualTo(3);
    assertThat(point.getSum()).isEqualTo(5);
    assertThat(point.getValues()).extracting(ValueAtQuantile::getValue).containsExactly(5.0);
  }

  @Test
  void metricIdentityIncludesResourceScopeMetadataAndType() {
    MetricData base = longSum("identity", delta(), longPoint(FIRST_ATTRIBUTES, 1));
    MetricData otherResource =
        ImmutableMetricData.createLongSum(
            Resource.create(Attributes.of(stringKey("service"), "b")),
            SCOPE,
            "identity",
            "",
            "",
            ImmutableSumData.create(
                true, AggregationTemporality.DELTA, singletonList(longPoint(FIRST_ATTRIBUTES, 1))));
    MetricData otherScope =
        ImmutableMetricData.createLongSum(
            RESOURCE,
            InstrumentationScopeInfo.builder("test.metrics.other").setVersion("1").build(),
            "identity",
            "",
            "",
            ImmutableSumData.create(
                true, AggregationTemporality.DELTA, singletonList(longPoint(FIRST_ATTRIBUTES, 1))));
    MetricData otherVersion =
        ImmutableMetricData.createLongSum(
            RESOURCE,
            InstrumentationScopeInfo.builder("test.metrics").setVersion("2").build(),
            "identity",
            "",
            "",
            ImmutableSumData.create(
                true, AggregationTemporality.DELTA, singletonList(longPoint(FIRST_ATTRIBUTES, 1))));
    MetricData otherDescription =
        ImmutableMetricData.createLongSum(
            RESOURCE,
            SCOPE,
            "identity",
            "different",
            "",
            ImmutableSumData.create(
                true, AggregationTemporality.DELTA, singletonList(longPoint(FIRST_ATTRIBUTES, 1))));
    MetricData otherUnit =
        ImmutableMetricData.createLongSum(
            RESOURCE,
            SCOPE,
            "identity",
            "",
            "requests",
            ImmutableSumData.create(
                true, AggregationTemporality.DELTA, singletonList(longPoint(FIRST_ATTRIBUTES, 1))));

    assertThat(
            aggregate(
                asList(base, otherResource, otherScope, otherVersion, otherDescription, otherUnit),
                singletonList(
                    ImmutableMetricData.createDoubleGauge(
                        RESOURCE, SCOPE, "identity", "", "", ImmutableGaugeData.empty()))))
        .hasSize(7);
  }

  @SafeVarargs
  @SuppressWarnings("varargs")
  private static List<MetricData> aggregate(List<MetricData>... collections) {
    return MetricDataAggregator.aggregate(asList(collections));
  }

  private static AggregationTemporality delta() {
    return AggregationTemporality.DELTA;
  }

  private static AggregationTemporality cumulative() {
    return AggregationTemporality.CUMULATIVE;
  }

  private static LongPointData longPoint(Attributes attributes, long value) {
    return ImmutableLongPointData.create(1, 2, attributes, value);
  }

  private static MetricData longSum(
      String name, AggregationTemporality temporality, LongPointData... points) {
    return ImmutableMetricData.createLongSum(
        RESOURCE, SCOPE, name, "", "", ImmutableSumData.create(true, temporality, asList(points)));
  }

  private static MetricData longGauge(String name, LongPointData... points) {
    return ImmutableMetricData.createLongGauge(
        RESOURCE, SCOPE, name, "", "", ImmutableGaugeData.create(asList(points)));
  }

  private static HistogramPointData histogramPoint(Attributes attributes, long count, double sum) {
    return ImmutableHistogramPointData.create(
        1, 2, attributes, sum, false, 0, false, 0, singletonList(1.0), asList(count, 0L));
  }

  private static MetricData histogramMetric(
      String name, AggregationTemporality temporality, HistogramPointData... points) {
    return ImmutableMetricData.createDoubleHistogram(
        RESOURCE,
        SCOPE,
        name,
        "",
        "ms",
        ImmutableHistogramData.create(temporality, asList(points)));
  }

  private static ExponentialHistogramPointData exponentialPoint(
      Attributes attributes, int scale, long count, double sum) {
    ExponentialHistogramBuckets buckets =
        ExponentialHistogramBuckets.create(scale, 0, singletonList(count));
    return ImmutableExponentialHistogramPointData.create(
        scale,
        sum,
        0,
        false,
        0,
        false,
        0,
        buckets,
        ExponentialHistogramBuckets.create(scale, 0, emptyList()),
        1,
        2,
        attributes,
        emptyList());
  }

  private static MetricData exponentialHistogram(
      String name, AggregationTemporality temporality, ExponentialHistogramPointData... points) {
    return ImmutableMetricData.createExponentialHistogram(
        RESOURCE,
        SCOPE,
        name,
        "",
        "",
        ImmutableExponentialHistogramData.create(temporality, asList(points)));
  }

  private static SummaryPointData summaryPoint(long count, double sum) {
    return ImmutableSummaryPointData.create(
        1, 2, FIRST_ATTRIBUTES, count, sum, singletonList(ValueAtQuantile.create(0.5, sum)));
  }

  private static MetricData summary(String name, SummaryPointData point) {
    return ImmutableMetricData.createDoubleSummary(
        RESOURCE, SCOPE, name, "", "", ImmutableSummaryData.create(singletonList(point)));
  }
}
