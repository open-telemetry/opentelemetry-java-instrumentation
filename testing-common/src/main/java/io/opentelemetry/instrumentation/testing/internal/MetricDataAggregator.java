/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.internal;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.DoubleExemplarData;
import io.opentelemetry.sdk.metrics.data.DoublePointData;
import io.opentelemetry.sdk.metrics.data.ExponentialHistogramBuckets;
import io.opentelemetry.sdk.metrics.data.ExponentialHistogramPointData;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.LongExemplarData;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableDoublePointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableExponentialHistogramBuckets;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableExponentialHistogramData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableExponentialHistogramPointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramPointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableLongPointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableSumData;
import io.opentelemetry.sdk.resources.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Normalizes captured metric collections for test assertions.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class MetricDataAggregator {

  public static List<MetricData> aggregate(List<List<MetricData>> collections) {
    if (collections.isEmpty()) {
      return new ArrayList<>();
    }
    Map<MetricIdentity, List<MetricSnapshot>> metrics = new LinkedHashMap<>();
    for (int collectionIndex = 0; collectionIndex < collections.size(); collectionIndex++) {
      for (MetricData metric : collections.get(collectionIndex)) {
        metrics
            .computeIfAbsent(new MetricIdentity(metric), unused -> new ArrayList<>())
            .add(new MetricSnapshot(collectionIndex, metric));
      }
    }

    List<MetricData> result = new ArrayList<>();
    int latestCollection = collections.size() - 1;
    for (List<MetricSnapshot> snapshots : metrics.values()) {
      MetricData latest = snapshots.get(snapshots.size() - 1).metric;
      validateCompatibility(snapshots);
      switch (latest.getType()) {
        case LONG_GAUGE:
        case DOUBLE_GAUGE:
        case SUMMARY:
          if (isLatestSnapshot(snapshots, latestCollection)) {
            result.add(latest);
          }
          break;
        case LONG_SUM:
        case DOUBLE_SUM:
          if (temporality(latest) == AggregationTemporality.DELTA) {
            result.add(aggregateSum(snapshots));
          } else if (isLatestSnapshot(snapshots, latestCollection)) {
            result.add(latest);
          }
          break;
        case HISTOGRAM:
          if (latest.getHistogramData().getAggregationTemporality()
              == AggregationTemporality.DELTA) {
            result.add(aggregateHistogram(snapshots));
          } else if (isLatestSnapshot(snapshots, latestCollection)) {
            result.add(latest);
          }
          break;
        case EXPONENTIAL_HISTOGRAM:
          if (latest.getExponentialHistogramData().getAggregationTemporality()
              == AggregationTemporality.DELTA) {
            result.add(aggregateExponentialHistogram(snapshots));
          } else if (isLatestSnapshot(snapshots, latestCollection)) {
            result.add(latest);
          }
          break;
      }
    }
    return result;
  }

  private static boolean isLatestSnapshot(List<MetricSnapshot> snapshots, int latestCollection) {
    return snapshots.get(snapshots.size() - 1).collectionIndex == latestCollection;
  }

  private static void validateCompatibility(List<MetricSnapshot> snapshots) {
    MetricData first = snapshots.get(0).metric;
    switch (first.getType()) {
      case LONG_SUM:
      case DOUBLE_SUM:
        AggregationTemporality temporality = temporality(first);
        boolean monotonic =
            first.getType() == MetricDataType.LONG_SUM
                ? first.getLongSumData().isMonotonic()
                : first.getDoubleSumData().isMonotonic();
        for (MetricSnapshot snapshot : snapshots) {
          MetricData metric = snapshot.metric;
          require(
              temporality(metric) == temporality, "sum temporality changed for " + first.getName());
          boolean metricMonotonic =
              metric.getType() == MetricDataType.LONG_SUM
                  ? metric.getLongSumData().isMonotonic()
                  : metric.getDoubleSumData().isMonotonic();
          require(metricMonotonic == monotonic, "sum monotonicity changed for " + first.getName());
        }
        break;
      case HISTOGRAM:
        AggregationTemporality histogramTemporality =
            first.getHistogramData().getAggregationTemporality();
        List<Double> histogramBoundaries = new ArrayList<>();
        boolean foundHistogramBoundaries = false;
        for (MetricSnapshot snapshot : snapshots) {
          require(
              snapshot.metric.getHistogramData().getAggregationTemporality()
                  == histogramTemporality,
              "histogram temporality changed for " + first.getName());
          for (HistogramPointData point : snapshot.metric.getHistogramData().getPoints()) {
            if (!foundHistogramBoundaries) {
              histogramBoundaries.addAll(point.getBoundaries());
              foundHistogramBoundaries = true;
            } else {
              require(
                  histogramBoundaries.equals(point.getBoundaries()),
                  "histogram boundaries changed for " + first.getName());
            }
          }
        }
        break;
      case EXPONENTIAL_HISTOGRAM:
        AggregationTemporality exponentialTemporality =
            first.getExponentialHistogramData().getAggregationTemporality();
        for (MetricSnapshot snapshot : snapshots) {
          require(
              snapshot.metric.getExponentialHistogramData().getAggregationTemporality()
                  == exponentialTemporality,
              "exponential histogram temporality changed for " + first.getName());
        }
        break;
      case LONG_GAUGE:
      case DOUBLE_GAUGE:
      case SUMMARY:
        break;
    }
  }

  private static MetricData aggregateSum(List<MetricSnapshot> snapshots) {
    MetricData first = snapshots.get(0).metric;
    AggregationTemporality temporality = temporality(first);
    if (first.getType() == MetricDataType.LONG_SUM) {
      boolean monotonic = first.getLongSumData().isMonotonic();
      Map<Attributes, LongPointAccumulator> points = new LinkedHashMap<>();
      for (MetricSnapshot snapshot : snapshots) {
        MetricData metric = snapshot.metric;
        require(
            temporality(metric) == temporality, "sum temporality changed for " + first.getName());
        require(
            metric.getLongSumData().isMonotonic() == monotonic,
            "sum monotonicity changed for " + first.getName());
        for (LongPointData point : metric.getLongSumData().getPoints()) {
          points
              .computeIfAbsent(point.getAttributes(), unused -> new LongPointAccumulator(point))
              .add(point);
        }
      }
      List<LongPointData> aggregatedPoints = new ArrayList<>();
      for (LongPointAccumulator point : points.values()) {
        aggregatedPoints.add(point.build());
      }
      return ImmutableMetricData.createLongSum(
          first.getResource(),
          first.getInstrumentationScopeInfo(),
          first.getName(),
          first.getDescription(),
          first.getUnit(),
          ImmutableSumData.create(monotonic, temporality, aggregatedPoints));
    }

    boolean monotonic = first.getDoubleSumData().isMonotonic();
    Map<Attributes, DoublePointAccumulator> points = new LinkedHashMap<>();
    for (MetricSnapshot snapshot : snapshots) {
      MetricData metric = snapshot.metric;
      require(temporality(metric) == temporality, "sum temporality changed for " + first.getName());
      require(
          metric.getDoubleSumData().isMonotonic() == monotonic,
          "sum monotonicity changed for " + first.getName());
      for (DoublePointData point : metric.getDoubleSumData().getPoints()) {
        points
            .computeIfAbsent(point.getAttributes(), unused -> new DoublePointAccumulator(point))
            .add(point);
      }
    }
    List<DoublePointData> aggregatedPoints = new ArrayList<>();
    for (DoublePointAccumulator point : points.values()) {
      aggregatedPoints.add(point.build());
    }
    return ImmutableMetricData.createDoubleSum(
        first.getResource(),
        first.getInstrumentationScopeInfo(),
        first.getName(),
        first.getDescription(),
        first.getUnit(),
        ImmutableSumData.create(monotonic, temporality, aggregatedPoints));
  }

  private static MetricData aggregateHistogram(List<MetricSnapshot> snapshots) {
    MetricData first = snapshots.get(0).metric;
    AggregationTemporality temporality = first.getHistogramData().getAggregationTemporality();
    Map<Attributes, HistogramPointAccumulator> points = new LinkedHashMap<>();
    for (MetricSnapshot snapshot : snapshots) {
      MetricData metric = snapshot.metric;
      require(
          metric.getHistogramData().getAggregationTemporality() == temporality,
          "histogram temporality changed for " + first.getName());
      for (HistogramPointData point : metric.getHistogramData().getPoints()) {
        HistogramPointAccumulator accumulator = points.get(point.getAttributes());
        if (accumulator == null) {
          points.put(point.getAttributes(), new HistogramPointAccumulator(point));
        } else {
          accumulator.add(point, first.getName());
        }
      }
    }
    List<HistogramPointData> aggregatedPoints = new ArrayList<>();
    for (HistogramPointAccumulator point : points.values()) {
      aggregatedPoints.add(point.build());
    }
    return ImmutableMetricData.createDoubleHistogram(
        first.getResource(),
        first.getInstrumentationScopeInfo(),
        first.getName(),
        first.getDescription(),
        first.getUnit(),
        ImmutableHistogramData.create(temporality, aggregatedPoints));
  }

  private static MetricData aggregateExponentialHistogram(List<MetricSnapshot> snapshots) {
    MetricData first = snapshots.get(0).metric;
    AggregationTemporality temporality =
        first.getExponentialHistogramData().getAggregationTemporality();
    Map<Attributes, ExponentialHistogramPointAccumulator> points = new LinkedHashMap<>();
    for (MetricSnapshot snapshot : snapshots) {
      MetricData metric = snapshot.metric;
      require(
          metric.getExponentialHistogramData().getAggregationTemporality() == temporality,
          "exponential histogram temporality changed for " + first.getName());
      for (ExponentialHistogramPointData point : metric.getExponentialHistogramData().getPoints()) {
        ExponentialHistogramPointAccumulator accumulator = points.get(point.getAttributes());
        if (accumulator == null) {
          points.put(point.getAttributes(), new ExponentialHistogramPointAccumulator(point));
        } else {
          accumulator.add(point);
        }
      }
    }
    List<ExponentialHistogramPointData> aggregatedPoints = new ArrayList<>();
    for (ExponentialHistogramPointAccumulator point : points.values()) {
      aggregatedPoints.add(point.build());
    }
    return ImmutableMetricData.createExponentialHistogram(
        first.getResource(),
        first.getInstrumentationScopeInfo(),
        first.getName(),
        first.getDescription(),
        first.getUnit(),
        ImmutableExponentialHistogramData.create(temporality, aggregatedPoints));
  }

  private static AggregationTemporality temporality(MetricData metric) {
    return metric.getType() == MetricDataType.LONG_SUM
        ? metric.getLongSumData().getAggregationTemporality()
        : metric.getDoubleSumData().getAggregationTemporality();
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
  }

  private static class MetricSnapshot {
    private final int collectionIndex;
    private final MetricData metric;

    private MetricSnapshot(int collectionIndex, MetricData metric) {
      this.collectionIndex = collectionIndex;
      this.metric = metric;
    }
  }

  private static class MetricIdentity {
    private final Resource resource;
    private final InstrumentationScopeInfo scope;
    private final String name;
    private final String description;
    private final String unit;
    private final MetricDataType type;

    private MetricIdentity(MetricData metric) {
      resource = metric.getResource();
      scope = metric.getInstrumentationScopeInfo();
      name = metric.getName();
      description = metric.getDescription();
      unit = metric.getUnit();
      type = metric.getType();
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof MetricIdentity)) {
        return false;
      }
      MetricIdentity that = (MetricIdentity) other;
      return resource.equals(that.resource)
          && scope.equals(that.scope)
          && name.equals(that.name)
          && description.equals(that.description)
          && unit.equals(that.unit)
          && type == that.type;
    }

    @Override
    public int hashCode() {
      return Objects.hash(resource, scope, name, description, unit, type);
    }
  }

  private static class LongPointAccumulator {
    private final Attributes attributes;
    private long start;
    private long end;
    private long value;
    private final List<LongExemplarData> exemplars = new ArrayList<>();

    private LongPointAccumulator(LongPointData point) {
      attributes = point.getAttributes();
      start = point.getStartEpochNanos();
      end = point.getEpochNanos();
    }

    private void add(LongPointData point) {
      start = Math.min(start, point.getStartEpochNanos());
      end = Math.max(end, point.getEpochNanos());
      value = Math.addExact(value, point.getValue());
      exemplars.addAll(point.getExemplars());
    }

    private LongPointData build() {
      return ImmutableLongPointData.create(start, end, attributes, value, exemplars);
    }
  }

  private static class DoublePointAccumulator {
    private final Attributes attributes;
    private long start;
    private long end;
    private double value;
    private final List<DoubleExemplarData> exemplars = new ArrayList<>();

    private DoublePointAccumulator(DoublePointData point) {
      attributes = point.getAttributes();
      start = point.getStartEpochNanos();
      end = point.getEpochNanos();
    }

    private void add(DoublePointData point) {
      start = Math.min(start, point.getStartEpochNanos());
      end = Math.max(end, point.getEpochNanos());
      value += point.getValue();
      exemplars.addAll(point.getExemplars());
    }

    private DoublePointData build() {
      return ImmutableDoublePointData.create(start, end, attributes, value, exemplars);
    }
  }

  private static class HistogramPointAccumulator {
    private final Attributes attributes;
    private final List<Double> boundaries;
    private final List<Long> counts;
    private long start;
    private long end;
    private long count;
    private double sum;
    private boolean hasMin;
    private double min;
    private boolean hasMax;
    private double max;
    private final List<DoubleExemplarData> exemplars = new ArrayList<>();

    private HistogramPointAccumulator(HistogramPointData point) {
      attributes = point.getAttributes();
      boundaries = point.getBoundaries();
      counts = new ArrayList<>(point.getCounts());
      start = point.getStartEpochNanos();
      end = point.getEpochNanos();
      count = point.getCount();
      sum = point.getSum();
      hasMin = point.hasMin();
      min = point.getMin();
      hasMax = point.hasMax();
      max = point.getMax();
      exemplars.addAll(point.getExemplars());
    }

    private void add(HistogramPointData point, String metricName) {
      require(
          boundaries.equals(point.getBoundaries()),
          "histogram boundaries changed for " + metricName);
      require(counts.size() == point.getCounts().size(), "histogram bucket count changed");
      start = Math.min(start, point.getStartEpochNanos());
      end = Math.max(end, point.getEpochNanos());
      count = Math.addExact(count, point.getCount());
      sum += point.getSum();
      for (int i = 0; i < counts.size(); i++) {
        counts.set(i, Math.addExact(counts.get(i), point.getCounts().get(i)));
      }
      if (hasMin && point.hasMin()) {
        min = Math.min(min, point.getMin());
      } else {
        hasMin = false;
      }
      if (hasMax && point.hasMax()) {
        max = Math.max(max, point.getMax());
      } else {
        hasMax = false;
      }
      exemplars.addAll(point.getExemplars());
    }

    private HistogramPointData build() {
      return ImmutableHistogramPointData.create(
          start, end, attributes, sum, hasMin, min, hasMax, max, boundaries, counts, exemplars);
    }
  }

  private static class ExponentialHistogramPointAccumulator {
    private final Attributes attributes;
    private int scale;
    private long start;
    private long end;
    private long count;
    private double sum;
    private long zeroCount;
    private boolean hasMin;
    private double min;
    private boolean hasMax;
    private double max;
    private final Map<Integer, Long> positiveBuckets = new TreeMap<>();
    private final Map<Integer, Long> negativeBuckets = new TreeMap<>();
    private final List<DoubleExemplarData> exemplars = new ArrayList<>();

    private ExponentialHistogramPointAccumulator(ExponentialHistogramPointData point) {
      attributes = point.getAttributes();
      scale = point.getScale();
      start = point.getStartEpochNanos();
      end = point.getEpochNanos();
      count = point.getCount();
      sum = point.getSum();
      zeroCount = point.getZeroCount();
      hasMin = point.hasMin();
      min = point.getMin();
      hasMax = point.hasMax();
      max = point.getMax();
      addBuckets(point.getPositiveBuckets(), positiveBuckets, scale);
      addBuckets(point.getNegativeBuckets(), negativeBuckets, scale);
      exemplars.addAll(point.getExemplars());
    }

    private void add(ExponentialHistogramPointData point) {
      int targetScale = Math.min(scale, point.getScale());
      rescaleBuckets(positiveBuckets, scale, targetScale);
      rescaleBuckets(negativeBuckets, scale, targetScale);
      scale = targetScale;
      addBuckets(point.getPositiveBuckets(), positiveBuckets, targetScale);
      addBuckets(point.getNegativeBuckets(), negativeBuckets, targetScale);
      start = Math.min(start, point.getStartEpochNanos());
      end = Math.max(end, point.getEpochNanos());
      count = Math.addExact(count, point.getCount());
      zeroCount = Math.addExact(zeroCount, point.getZeroCount());
      sum += point.getSum();
      if (hasMin && point.hasMin()) {
        min = Math.min(min, point.getMin());
      } else {
        hasMin = false;
      }
      if (hasMax && point.hasMax()) {
        max = Math.max(max, point.getMax());
      } else {
        hasMax = false;
      }
      exemplars.addAll(point.getExemplars());
    }

    private ExponentialHistogramPointData build() {
      ExponentialHistogramBuckets positive = buckets(positiveBuckets);
      ExponentialHistogramBuckets negative = buckets(negativeBuckets);
      require(
          count
              == Math.addExact(
                  zeroCount, Math.addExact(positive.getTotalCount(), negative.getTotalCount())),
          "exponential histogram count does not match its buckets");
      return ImmutableExponentialHistogramPointData.create(
          scale,
          sum,
          zeroCount,
          hasMin,
          min,
          hasMax,
          max,
          positive,
          negative,
          start,
          end,
          attributes,
          exemplars);
    }

    private ExponentialHistogramBuckets buckets(Map<Integer, Long> buckets) {
      if (buckets.isEmpty()) {
        return ImmutableExponentialHistogramBuckets.create(scale, 0, new ArrayList<>());
      }
      int minIndex = buckets.keySet().stream().mapToInt(Integer::intValue).min().getAsInt();
      int maxIndex = buckets.keySet().stream().mapToInt(Integer::intValue).max().getAsInt();
      long size = (long) maxIndex - minIndex + 1;
      require(size <= 1_000_000, "exponential histogram has an unreasonable bucket range");
      List<Long> counts = new ArrayList<>((int) size);
      for (int index = minIndex; index <= maxIndex; index++) {
        counts.add(buckets.getOrDefault(index, 0L));
      }
      return ImmutableExponentialHistogramBuckets.create(scale, minIndex, counts);
    }

    private static void addBuckets(
        ExponentialHistogramBuckets buckets, Map<Integer, Long> target, int targetScale) {
      int sourceScale = buckets.getScale();
      int scaleDifference = sourceScale - targetScale;
      require(scaleDifference >= 0 && scaleDifference < 63, "invalid exponential histogram scale");
      long scaleFactor = 1L << scaleDifference;
      List<Long> counts = buckets.getBucketCounts();
      for (int i = 0; i < counts.size(); i++) {
        long count = counts.get(i);
        if (count == 0) {
          continue;
        }
        long sourceIndex = (long) buckets.getOffset() + i;
        long targetIndex = Math.floorDiv(sourceIndex, scaleFactor);
        require(
            targetIndex >= Integer.MIN_VALUE && targetIndex <= Integer.MAX_VALUE,
            "exponential histogram bucket index out of range");
        int index = (int) targetIndex;
        target.merge(index, count, Math::addExact);
      }
    }

    private static void rescaleBuckets(
        Map<Integer, Long> buckets, int sourceScale, int targetScale) {
      if (sourceScale == targetScale) {
        return;
      }
      long scaleFactor = 1L << (sourceScale - targetScale);
      Map<Integer, Long> rescaled = new TreeMap<>();
      for (Map.Entry<Integer, Long> bucket : buckets.entrySet()) {
        long index = Math.floorDiv((long) bucket.getKey(), scaleFactor);
        require(
            index >= Integer.MIN_VALUE && index <= Integer.MAX_VALUE,
            "exponential histogram bucket index out of range");
        rescaled.merge((int) index, bucket.getValue(), Math::addExact);
      }
      buckets.clear();
      buckets.putAll(rescaled);
    }
  }

  private MetricDataAggregator() {}
}
