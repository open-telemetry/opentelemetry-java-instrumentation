/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.internal;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.asRemote;
import static io.opentelemetry.testing.internal.proto.trace.v1.SpanFlags.SPAN_FLAGS_CONTEXT_HAS_IS_REMOTE_MASK_VALUE;
import static io.opentelemetry.testing.internal.proto.trace.v1.SpanFlags.SPAN_FLAGS_CONTEXT_IS_REMOTE_MASK_VALUE;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.exporter.internal.otlp.traces.TraceRequestMarshaler;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.testing.trace.TestSpanData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.internal.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.testing.internal.proto.common.v1.AnyValue;
import io.opentelemetry.testing.internal.proto.common.v1.KeyValue;
import io.opentelemetry.testing.internal.proto.metrics.v1.ExponentialHistogram;
import io.opentelemetry.testing.internal.proto.metrics.v1.ExponentialHistogramDataPoint;
import io.opentelemetry.testing.internal.proto.metrics.v1.Metric;
import io.opentelemetry.testing.internal.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.testing.internal.proto.metrics.v1.ScopeMetrics;
import io.opentelemetry.testing.internal.proto.trace.v1.ResourceSpans;
import io.opentelemetry.testing.internal.proto.trace.v1.ScopeSpans;
import io.opentelemetry.testing.internal.proto.trace.v1.Span;
import io.opentelemetry.testing.internal.protobuf.ByteString;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class TelemetryConverterTest {

  private static final String TRACE_ID = "0102030405060708090a0b0c0d0e0f10";
  private static final String SPAN_ID = "0102030405060708";
  private static final TraceState TRACE_STATE =
      TraceState.builder().put("vendor", "value").put("other", "entry").build();

  @ParameterizedTest
  @MethodSource("linkFlags")
  void linkContext(int flags, boolean remote) {
    Span.Link link =
        Span.Link.newBuilder()
            .setTraceId(
                ByteString.copyFrom(
                    new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}))
            .setSpanId(ByteString.copyFrom(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}))
            .setFlags(flags)
            .setTraceState("other=entry,vendor=value")
            .addAttributes(
                KeyValue.newBuilder()
                    .setKey("link.attribute")
                    .setValue(AnyValue.newBuilder().setStringValue("value")))
            .setDroppedAttributesCount(2)
            .build();
    Span span =
        Span.newBuilder()
            .setTraceId(link.getTraceId())
            .setSpanId(link.getSpanId())
            .setKind(Span.SpanKind.SPAN_KIND_INTERNAL)
            .setFlags(
                SPAN_FLAGS_CONTEXT_HAS_IS_REMOTE_MASK_VALUE
                    | SPAN_FLAGS_CONTEXT_IS_REMOTE_MASK_VALUE
                    | 1)
            .addLinks(link)
            .setDroppedLinksCount(3)
            .build();

    assertThat(
            TelemetryConverter.getSpanData(
                singletonList(
                    ResourceSpans.newBuilder()
                        .addScopeSpans(ScopeSpans.newBuilder().addSpans(span))
                        .build())))
        .singleElement()
        .satisfies(
            spanData -> {
              assertThat(spanData.getSpanContext())
                  .isEqualTo(
                      SpanContext.create(
                          TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault()));
              assertThat(spanData.getTotalRecordedLinks()).isEqualTo(4);
              assertThat(spanData.getLinks())
                  .containsExactly(
                      LinkData.create(
                          remote
                              ? SpanContext.createFromRemoteParent(
                                  TRACE_ID, SPAN_ID, TraceFlags.fromByte((byte) flags), TRACE_STATE)
                              : SpanContext.create(
                                  TRACE_ID,
                                  SPAN_ID,
                                  TraceFlags.fromByte((byte) flags),
                                  TRACE_STATE),
                          Attributes.of(stringKey("link.attribute"), "value"),
                          3));
            });
  }

  private static Stream<Arguments> linkFlags() {
    return Stream.of(
        argumentSet("absent flags", 0, false),
        argumentSet("trace flags only", 0x81, false),
        argumentSet("known local", 0x181, false),
        argumentSet("known remote", 0x381, true),
        argumentSet("unsampled remote", 0x380, true),
        argumentSet("remote bit without known bit", 0x281, false),
        argumentSet("reserved bits", 0x80000381, true));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void sdkOtlpRoundTrip(boolean sampled) throws IOException {
    SpanContext local =
        SpanContext.create(
            TRACE_ID,
            SPAN_ID,
            sampled ? TraceFlags.getSampled() : TraceFlags.getDefault(),
            TRACE_STATE);
    SpanContext remote =
        SpanContext.createFromRemoteParent(TRACE_ID, SPAN_ID, local.getTraceFlags(), TRACE_STATE);
    assertThat(asRemote(local)).isEqualTo(remote);
    SpanData original =
        TestSpanData.builder()
            .setName("span")
            .setKind(SpanKind.INTERNAL)
            .setStatus(StatusData.unset())
            .setHasEnded(true)
            .setStartEpochNanos(1)
            .setEndEpochNanos(2)
            .setSpanContext(local)
            .setParentSpanContext(remote)
            .setLinks(
                asList(
                    LinkData.create(
                        remote, Attributes.of(stringKey("remote.attribute"), "value"), 3),
                    LinkData.create(
                        local, Attributes.of(stringKey("local.attribute"), "value"), 5)))
            .setTotalRecordedLinks(4)
            .build();
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    TraceRequestMarshaler.create(singletonList(original)).writeBinaryTo(output);
    ExportTraceServiceRequest request = ExportTraceServiceRequest.parseFrom(output.toByteArray());

    assertThat(TelemetryConverter.getSpanData(request.getResourceSpansList()))
        .singleElement()
        .satisfies(
            spanData -> {
              assertThat(spanData.getSpanContext()).isEqualTo(local);
              assertThat(spanData.getLinks()).containsExactlyElementsOf(original.getLinks());
              assertThat(spanData.getTotalRecordedLinks()).isEqualTo(4);
            });
  }

  @Test
  void exponentialHistogram() {
    MetricData metric =
        convertExponentialHistogram(
            io.opentelemetry.testing.internal.proto.metrics.v1.AggregationTemporality
                .AGGREGATION_TEMPORALITY_DELTA,
            ExponentialHistogramDataPoint.newBuilder()
                .setStartTimeUnixNano(10)
                .setTimeUnixNano(20)
                .addAttributes(
                    KeyValue.newBuilder()
                        .setKey("key")
                        .setValue(AnyValue.newBuilder().setStringValue("value")))
                .setCount(11)
                .setSum(12.5)
                .setScale(2)
                .setZeroCount(3)
                .setPositive(
                    ExponentialHistogramDataPoint.Buckets.newBuilder()
                        .setOffset(-2)
                        .addAllBucketCounts(asList(0L, 2L, 0L)))
                .setNegative(
                    ExponentialHistogramDataPoint.Buckets.newBuilder()
                        .setOffset(5)
                        .addAllBucketCounts(asList(1L, 0L, 5L)))
                .setMin(-1.5)
                .setMax(4.5)
                .build());

    assertThat(metric.getName()).isEqualTo("name");
    assertThat(metric.getDescription()).isEqualTo("description");
    assertThat(metric.getUnit()).isEqualTo("unit");
    assertThat(metric.getExponentialHistogramData().getAggregationTemporality())
        .isEqualTo(AggregationTemporality.DELTA);
    assertThat(metric.getExponentialHistogramData().getPoints())
        .singleElement()
        .satisfies(
            point -> {
              assertThat(point.getStartEpochNanos()).isEqualTo(10);
              assertThat(point.getEpochNanos()).isEqualTo(20);
              assertThat(point.getAttributes()).isEqualTo(Attributes.of(stringKey("key"), "value"));
              assertThat(point.getCount()).isEqualTo(11);
              assertThat(point.getSum()).isEqualTo(12.5);
              assertThat(point.getScale()).isEqualTo(2);
              assertThat(point.getZeroCount()).isEqualTo(3);
              assertThat(point.hasMin()).isTrue();
              assertThat(point.getMin()).isEqualTo(-1.5);
              assertThat(point.hasMax()).isTrue();
              assertThat(point.getMax()).isEqualTo(4.5);
              assertThat(point.getPositiveBuckets().getScale()).isEqualTo(2);
              assertThat(point.getPositiveBuckets().getOffset()).isEqualTo(-2);
              assertThat(point.getPositiveBuckets().getBucketCounts()).containsExactly(0L, 2L, 0L);
              assertThat(point.getNegativeBuckets().getScale()).isEqualTo(2);
              assertThat(point.getNegativeBuckets().getOffset()).isEqualTo(5);
              assertThat(point.getNegativeBuckets().getBucketCounts()).containsExactly(1L, 0L, 5L);
            });
  }

  @Test
  void exponentialHistogramWithoutOptionalStatisticsAndBuckets() {
    MetricData metric =
        convertExponentialHistogram(
            io.opentelemetry.testing.internal.proto.metrics.v1.AggregationTemporality
                .AGGREGATION_TEMPORALITY_CUMULATIVE,
            ExponentialHistogramDataPoint.newBuilder().setCount(2).setZeroCount(2).build());

    assertThat(metric.getExponentialHistogramData().getAggregationTemporality())
        .isEqualTo(AggregationTemporality.CUMULATIVE);
    assertThat(metric.getExponentialHistogramData().getPoints())
        .singleElement()
        .satisfies(
            point -> {
              assertThat(point.getCount()).isEqualTo(2);
              assertThat(point.getZeroCount()).isEqualTo(2);
              assertThat(point.hasMin()).isFalse();
              assertThat(point.hasMax()).isFalse();
              assertThat(point.getPositiveBuckets().getBucketCounts()).isEmpty();
              assertThat(point.getNegativeBuckets().getBucketCounts()).isEmpty();
            });
  }

  private static MetricData convertExponentialHistogram(
      io.opentelemetry.testing.internal.proto.metrics.v1.AggregationTemporality temporality,
      ExponentialHistogramDataPoint point) {
    ResourceMetrics resourceMetrics =
        ResourceMetrics.newBuilder()
            .addScopeMetrics(
                ScopeMetrics.newBuilder()
                    .addMetrics(
                        Metric.newBuilder()
                            .setName("name")
                            .setDescription("description")
                            .setUnit("unit")
                            .setExponentialHistogram(
                                ExponentialHistogram.newBuilder()
                                    .setAggregationTemporality(temporality)
                                    .addDataPoints(point))))
            .build();
    Collection<MetricData> metrics =
        TelemetryConverter.getMetricsData(singletonList(resourceMetrics));
    assertThat(metrics).hasSize(1);
    MetricData metric = metrics.iterator().next();
    assertThat(metric.getType()).isEqualTo(MetricDataType.EXPONENTIAL_HISTOGRAM);
    return metric;
  }
}
