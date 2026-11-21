/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramPointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableLongPointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableSumData;
import io.opentelemetry.sdk.resources.Resource;
import java.util.function.Consumer;
import org.assertj.core.api.ListAssert;
import org.junit.jupiter.api.Test;

class MessagingMetricsAssertionsTest {

  private static final Attributes FIRST = Attributes.of(stringKey("destination"), "first");
  private static final Attributes SECOND = Attributes.of(stringKey("destination"), "second");
  private static final InstrumentationScopeInfo SCOPE = InstrumentationScopeInfo.create("scope");

  @Test
  void histogramMatchesAllPointsInAnyOrder() {
    InstrumentationExtension testing = testing(histogram(point(FIRST, 1), point(SECOND, 1)));
    MessagingMetricsAssertions.assertHistogram(testing, "scope", "metric", SECOND, FIRST);
    MessagingMetricsAssertions.assertHistogram(
        testing(histogram(point(FIRST, 1), point(FIRST, 1))), "scope", "metric", FIRST, FIRST);
    assertThatThrownBy(
            () -> MessagingMetricsAssertions.assertHistogram(testing, "scope", "metric", FIRST))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() ->
            MessagingMetricsAssertions.assertHistogram(testing, "scope", "metric", FIRST, FIRST))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() ->
            MessagingMetricsAssertions.assertHistogram(
                testing(histogram(point(FIRST, 2))), "scope", "metric", FIRST))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void emptyHistogramExpectationPreservesWrongTypeBehavior() {
    MessagingMetricsAssertions.assertHistogram(testing(histogram()), "scope", "metric");
    MessagingMetricsAssertions.assertHistogram(testing(counter(1, FIRST)), "scope", "metric");
    assertThatThrownBy(() ->
            MessagingMetricsAssertions.assertHistogram(
                testing(histogram(point(FIRST, 1))), "scope", "metric"))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() ->
            MessagingMetricsAssertions.assertHistogram(
                testing(counter(1, FIRST)), "scope", "metric", FIRST))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void counterMatchesValueAndExactAttributes() {
    InstrumentationExtension testing = testing(counter(2, FIRST));
    MessagingMetricsAssertions.assertCounter(testing, "scope", "metric", 2, FIRST);
    assertThatThrownBy(
            () -> MessagingMetricsAssertions.assertCounter(testing, "scope", "metric", 1, FIRST))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() ->
            MessagingMetricsAssertions.assertCounter(
                testing, "scope", "metric", 2, Attributes.empty()))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() ->
            MessagingMetricsAssertions.assertCounter(
                testing(histogram(point(FIRST, 1))), "scope", "metric", 2, FIRST))
        .isInstanceOf(AssertionError.class);
  }

  private static InstrumentationExtension testing(MetricData metric) {
    InstrumentationExtension testing = mock(InstrumentationExtension.class);
    doAnswer(
            invocation -> {
              assertThat(invocation.<String>getArgument(0)).isEqualTo("scope");
              assertThat(invocation.<String>getArgument(1)).isEqualTo("metric");
              Consumer<ListAssert<MetricData>> assertion = invocation.getArgument(2);
              assertion.accept(assertThat(singletonList(metric)));
              return null;
            })
        .when(testing)
        .waitAndAssertMetrics(anyString(), anyString(), any());
    return testing;
  }

  private static MetricData histogram(HistogramPointData... points) {
    return ImmutableMetricData.createDoubleHistogram(
        Resource.empty(),
        SCOPE,
        "metric",
        "",
        "",
        ImmutableHistogramData.create(AggregationTemporality.CUMULATIVE, asList(points)));
  }

  private static HistogramPointData point(Attributes attributes, long count) {
    return ImmutableHistogramPointData.create(
        0, 1, attributes, 1, false, 0, false, 0, emptyList(), singletonList(count));
  }

  private static MetricData counter(long value, Attributes attributes) {
    return ImmutableMetricData.createLongSum(
        Resource.empty(),
        SCOPE,
        "metric",
        "",
        "",
        ImmutableSumData.create(
            true,
            AggregationTemporality.CUMULATIVE,
            singletonList(ImmutableLongPointData.create(0, 1, attributes, value))));
  }
}
