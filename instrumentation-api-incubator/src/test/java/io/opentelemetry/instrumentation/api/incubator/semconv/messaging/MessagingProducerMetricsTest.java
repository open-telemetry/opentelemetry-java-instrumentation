/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.SEND;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.CLIENT_OPERATION_DURATION;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.SENT_MESSAGES;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetryState.contains;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetryState.enable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_TEMPLATE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MessagingProducerMetricsTest {

  private static final double[] DURATION_BUCKETS =
      MessagingMetricsAdvice.DURATION_SECONDS_BUCKETS.stream().mapToDouble(d -> d).toArray();

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void collectsMetrics() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    cleanup.deferCleanup(meterProvider);
    OperationListener listener = MessagingProducerMetrics.get().create(meterProvider.get("test"));

    Attributes requestAttributes =
        Attributes.builder()
            .put(MESSAGING_SYSTEM, "pulsar")
            .put(MESSAGING_DESTINATION_NAME, "topic")
            .put(MESSAGING_DESTINATION_TEMPLATE, "topic-{id}")
            .put(MESSAGING_OPERATION_NAME, "send")
            .put(MESSAGING_OPERATION_TYPE, "send")
            .put(SERVER_ADDRESS, "localhost")
            .put(SERVER_PORT, 6650)
            .build();
    Attributes responseAttributes =
        Attributes.builder()
            .put(MESSAGING_DESTINATION_PARTITION_ID, "1")
            .put(MESSAGING_BATCH_MESSAGE_COUNT, 2)
            .put(ERROR_TYPE, IllegalStateException.class.getName())
            .build();

    Context parent =
        enable(
            Context.root()
                .with(
                    Span.wrap(
                        SpanContext.create(
                            "ff01020304050600ff0a0b0c0d0e0f00",
                            "090a0b0c0d0e0f00",
                            TraceFlags.getSampled(),
                            TraceState.getDefault()))));

    Context context = listener.onStart(parent, requestAttributes, nanos(100));
    assertThat(contains(context, SEND, CLIENT_OPERATION_DURATION)).isTrue();
    assertThat(contains(context, SEND, SENT_MESSAGES)).isTrue();
    listener.onEnd(context, responseAttributes, nanos(250));

    Collection<MetricData> metrics = metricReader.collectAllMetrics();
    assertThat(metrics).hasSize(2);
    assertThat(metrics)
        .anySatisfy(
            metric ->
                assertThat(metric)
                    .hasName("messaging.client.operation.duration")
                    .hasUnit("s")
                    .hasDescription(
                        "Duration of messaging operation initiated by a producer or consumer client.")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSum(0.15)
                                        .hasBucketBoundaries(DURATION_BUCKETS)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(MESSAGING_OPERATION_NAME, "send"),
                                            equalTo(MESSAGING_SYSTEM, "pulsar"),
                                            equalTo(MESSAGING_DESTINATION_TEMPLATE, "topic-{id}"),
                                            equalTo(MESSAGING_OPERATION_TYPE, "send"),
                                            equalTo(
                                                ERROR_TYPE, IllegalStateException.class.getName()),
                                            equalTo(MESSAGING_DESTINATION_PARTITION_ID, "1"),
                                            equalTo(SERVER_ADDRESS, "localhost"),
                                            equalTo(SERVER_PORT, 6650))
                                        .hasExemplarsSatisfying(
                                            exemplar ->
                                                exemplar
                                                    .hasTraceId("ff01020304050600ff0a0b0c0d0e0f00")
                                                    .hasSpanId("090a0b0c0d0e0f00")))))
        .anySatisfy(
            metric ->
                assertThat(metric)
                    .hasName("messaging.client.sent.messages")
                    .hasUnit("{message}")
                    .hasDescription("Number of messages producer attempted to send to the broker.")
                    .hasLongSumSatisfying(
                        sum ->
                            sum.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasValue(2)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(MESSAGING_OPERATION_NAME, "send"),
                                            equalTo(MESSAGING_SYSTEM, "pulsar"),
                                            equalTo(
                                                ERROR_TYPE, IllegalStateException.class.getName()),
                                            equalTo(MESSAGING_DESTINATION_TEMPLATE, "topic-{id}"),
                                            equalTo(MESSAGING_DESTINATION_PARTITION_ID, "1"),
                                            equalTo(SERVER_ADDRESS, "localhost"),
                                            equalTo(SERVER_PORT, 6650)))));
  }

  @Test
  void outerOperationPreventsDuplicateNestedMetrics() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    cleanup.deferCleanup(meterProvider);
    OperationListener outer = MessagingProducerMetrics.get().create(meterProvider.get("outer"));
    OperationListener inner = MessagingProducerMetrics.get().create(meterProvider.get("inner"));
    Attributes attributes =
        Attributes.builder()
            .put(MESSAGING_SYSTEM, "kafka")
            .put(MESSAGING_OPERATION_NAME, "send")
            .put(MESSAGING_OPERATION_TYPE, "send")
            .build();

    Context outerContext = outer.onStart(enable(Context.root()), attributes, nanos(100));
    Context innerContext = inner.onStart(outerContext, attributes, nanos(150));
    inner.onEnd(innerContext, Attributes.empty(), nanos(200));
    outer.onEnd(outerContext, Attributes.empty(), nanos(250));

    Collection<MetricData> metrics = metricReader.collectAllMetrics();
    assertThat(metrics)
        .allMatch(metric -> metric.getInstrumentationScopeInfo().getName().equals("outer"))
        .extracting(MetricData::getName)
        .containsExactlyInAnyOrder(
            "messaging.client.operation.duration", "messaging.client.sent.messages");
  }

  @Test
  void nestedProducerOperationsRecordIndependentlyByDefault() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    cleanup.deferCleanup(meterProvider);
    OperationListener outer = MessagingProducerMetrics.get().create(meterProvider.get("outer"));
    OperationListener inner = MessagingProducerMetrics.get().create(meterProvider.get("inner"));
    Attributes attributes =
        Attributes.builder()
            .put(MESSAGING_OPERATION_NAME, "send")
            .put(MESSAGING_OPERATION_TYPE, "send")
            .build();

    Context outerContext = outer.onStart(Context.root(), attributes, nanos(100));
    Context innerContext = inner.onStart(outerContext, attributes, nanos(150));
    inner.onEnd(innerContext, Attributes.empty(), nanos(200));
    outer.onEnd(outerContext, Attributes.empty(), nanos(250));

    Collection<MetricData> metrics = metricReader.collectAllMetrics();
    assertThat(metrics)
        .extracting(metric -> metric.getInstrumentationScopeInfo().getName())
        .containsExactlyInAnyOrder("outer", "outer", "inner", "inner");
  }

  @Test
  void completedOperationDoesNotSuppressSibling() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    cleanup.deferCleanup(meterProvider);
    OperationListener listener = MessagingProducerMetrics.get().create(meterProvider.get("test"));
    Attributes attributes =
        Attributes.builder()
            .put(MESSAGING_SYSTEM, "kafka")
            .put(MESSAGING_OPERATION_NAME, "send")
            .put(MESSAGING_OPERATION_TYPE, "send")
            .build();

    Context first = listener.onStart(Context.root(), attributes, nanos(100));
    listener.onEnd(first, Attributes.empty(), nanos(150));
    Context second = listener.onStart(Context.root(), attributes, nanos(200));
    listener.onEnd(second, Attributes.empty(), nanos(250));

    Collection<MetricData> metrics = metricReader.collectAllMetrics();
    assertThat(metrics)
        .filteredOn(metric -> metric.getName().equals("messaging.client.sent.messages"))
        .singleElement()
        .satisfies(
            metric ->
                assertThat(metric)
                    .hasLongSumSatisfying(
                        sum -> sum.hasPointsSatisfying(point -> point.hasValue(2))));
  }

  @Test
  void createDoesNotCountSentMessages() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    cleanup.deferCleanup(meterProvider);
    OperationListener listener = MessagingProducerMetrics.get().create(meterProvider.get("test"));

    Attributes attributes =
        Attributes.builder()
            .put(MESSAGING_OPERATION_NAME, "create")
            .put(MESSAGING_OPERATION_TYPE, "create")
            .build();
    Context context = listener.onStart(Context.root(), attributes, nanos(100));
    listener.onEnd(context, Attributes.empty(), nanos(250));

    Collection<MetricData> metrics = metricReader.collectAllMetrics();
    assertThat(metrics)
        .extracting(MetricData::getName)
        .containsExactly("messaging.client.operation.duration");
  }

  @Test
  void zeroBatchDoesNotCountSentMessages() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    cleanup.deferCleanup(meterProvider);
    OperationListener listener = MessagingProducerMetrics.get().create(meterProvider.get("test"));

    Attributes attributes =
        Attributes.builder()
            .put(MESSAGING_OPERATION_NAME, "send")
            .put(MESSAGING_OPERATION_TYPE, "send")
            .put(MESSAGING_BATCH_MESSAGE_COUNT, 0)
            .build();
    Context context = listener.onStart(Context.root(), attributes, nanos(100));
    listener.onEnd(context, Attributes.empty(), nanos(250));

    assertThat(metricReader.collectAllMetrics())
        .noneSatisfy(metric -> assertThat(metric).hasName("messaging.client.sent.messages"));
  }

  @Test
  void sentMessagesOnlyDoesNotRecordDuration() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.createDelta();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    cleanup.deferCleanup(meterProvider);
    OperationListener listener =
        MessagingProducerMetrics.getSentMessages().create(meterProvider.get("test"));

    Attributes attributes =
        Attributes.builder()
            .put(MESSAGING_SYSTEM, "kafka")
            .put(MESSAGING_OPERATION_NAME, "send")
            .put(MESSAGING_OPERATION_TYPE, "send")
            .build();
    Context context = listener.onStart(Context.root(), attributes, nanos(100));
    listener.onEnd(context, Attributes.empty(), nanos(250));

    Collection<MetricData> metrics = metricReader.collectAllMetrics();
    assertThat(metrics)
        .satisfiesExactly(
            metric ->
                assertThat(metric)
                    .hasName("messaging.client.sent.messages")
                    .hasLongSumSatisfying(
                        sum -> sum.hasPointsSatisfying(point -> point.hasValue(1))));
  }

  private static long nanos(int millis) {
    return MILLISECONDS.toNanos(millis);
  }
}
