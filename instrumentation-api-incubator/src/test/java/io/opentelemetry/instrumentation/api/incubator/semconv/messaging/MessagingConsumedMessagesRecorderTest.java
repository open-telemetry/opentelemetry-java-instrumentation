/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.RECEIVE;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.CONSUMED_MESSAGES;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_ANONYMOUS;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_SUBSCRIPTION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_TEMPLATE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_TEMPORARY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.logging.Level.WARNING;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetryState;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.internal.OperationMetricsUtil;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("deprecation") // using deprecated semconv
class MessagingConsumedMessagesRecorderTest {
  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void recordsExplicitCountWithAdviceAndContext(boolean sampled) {
    InMemoryMetricReader reader = InMemoryMetricReader.createDelta();
    SdkMeterProvider provider = SdkMeterProvider.builder().registerMetricReader(reader).build();
    cleanup.deferCleanup(provider);
    MessagingConsumedMessagesRecorder recorder =
        MessagingConsumedMessagesRecorder.create(
            provider.meterBuilder("consumer").setInstrumentationVersion("1.2.3").build());
    Attributes start =
        Attributes.builder()
            .put(MESSAGING_BATCH_MESSAGE_COUNT, 5)
            .put(MESSAGING_SYSTEM, "kafka")
            .put(MESSAGING_OPERATION_NAME, "receive")
            .put(MESSAGING_OPERATION_TYPE, "receive")
            .put(MESSAGING_CONSUMER_GROUP_NAME, "group")
            .put(MESSAGING_DESTINATION_SUBSCRIPTION_NAME, "subscription")
            .put(MESSAGING_DESTINATION_PARTITION_ID, "0")
            .put(MESSAGING_DESTINATION_NAME, "orders-42")
            .put(SERVER_ADDRESS, "localhost")
            .put(SERVER_PORT, 9092)
            .put("unbounded", "value")
            .build();
    Attributes end =
        Attributes.builder()
            .put(MESSAGING_OPERATION_NAME, "process")
            .put(MESSAGING_OPERATION_TYPE, "process")
            .put(MESSAGING_DESTINATION_TEMPLATE, "orders-{id}")
            .put(ERROR_TYPE, "failure")
            .build();
    SpanContext span =
        SpanContext.create(
            "ff01020304050600ff0a0b0c0d0e0f00",
            "090a0b0c0d0e0f00",
            sampled ? TraceFlags.getSampled() : TraceFlags.getDefault(),
            TraceState.getDefault());
    Context context = Context.root().with(Span.wrap(span));

    assertThat(recorder.record(2, start, end, context)).isEqualTo(emitStableMessagingSemconv());
    assertThat(start.get(MESSAGING_BATCH_MESSAGE_COUNT)).isEqualTo(5);
    assertThat(start.get(MESSAGING_OPERATION_NAME)).isEqualTo("receive");
    assertThat(start.get(MESSAGING_DESTINATION_NAME)).isEqualTo("orders-42");
    assertThat(end.get(MESSAGING_OPERATION_NAME)).isEqualTo("process");

    Collection<MetricData> metrics = reader.collectAllMetrics();
    if (!emitStableMessagingSemconv()) {
      assertThat(metrics).isEmpty();
      return;
    }
    assertThat(metrics)
        .satisfiesExactly(
            metric -> {
              assertThat(metric.getInstrumentationScopeInfo().getName()).isEqualTo("consumer");
              assertThat(metric.getInstrumentationScopeInfo().getVersion()).isEqualTo("1.2.3");
              assertThat(metric)
                  .hasName("messaging.client.consumed.messages")
                  .hasDescription("Number of messages that were delivered to the application.")
                  .hasUnit("{message}")
                  .hasLongSumSatisfying(
                      sum ->
                          sum.isMonotonic()
                              .hasPointsSatisfying(
                                  point -> {
                                    point
                                        .hasValue(2)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(MESSAGING_SYSTEM, "kafka"),
                                            equalTo(MESSAGING_OPERATION_NAME, "process"),
                                            equalTo(MESSAGING_CONSUMER_GROUP_NAME, "group"),
                                            equalTo(
                                                MESSAGING_DESTINATION_SUBSCRIPTION_NAME,
                                                "subscription"),
                                            equalTo(MESSAGING_DESTINATION_PARTITION_ID, "0"),
                                            equalTo(MESSAGING_DESTINATION_TEMPLATE, "orders-{id}"),
                                            equalTo(ERROR_TYPE, "failure"),
                                            equalTo(SERVER_ADDRESS, "localhost"),
                                            equalTo(SERVER_PORT, 9092));
                                    if (sampled) {
                                      point.hasExemplarsSatisfying(
                                          exemplar ->
                                              exemplar
                                                  .hasValue(2)
                                                  .hasTraceId(span.getTraceId())
                                                  .hasSpanId(span.getSpanId()));
                                    } else {
                                      point.hasExemplarsSatisfying();
                                    }
                                  }));
            });
  }

  @ParameterizedTest
  @MethodSource("destinations")
  void filtersDestinationAfterMerging(Attributes start, Attributes end, Attributes expected) {
    InMemoryMetricReader reader = InMemoryMetricReader.createDelta();
    SdkMeterProvider provider = SdkMeterProvider.builder().registerMetricReader(reader).build();
    cleanup.deferCleanup(provider);
    MessagingConsumedMessagesRecorder recorder =
        MessagingConsumedMessagesRecorder.create(provider.get("test"));

    assertThat(recorder.record(2, start, end, Context.root()))
        .isEqualTo(emitStableMessagingSemconv());

    Collection<MetricData> metrics = reader.collectAllMetrics();
    if (emitStableMessagingSemconv()) {
      assertThat(metrics)
          .satisfiesExactly(
              metric ->
                  assertThat(metric)
                      .hasLongSumSatisfying(
                          sum ->
                              sum.hasPointsSatisfying(
                                  point -> point.hasValue(2).hasAttributes(expected))));
    } else {
      assertThat(metrics).isEmpty();
    }
  }

  private static Stream<Arguments> destinations() {
    return Stream.of(
        argumentSet(
            "ordinary destination",
            Attributes.of(MESSAGING_DESTINATION_NAME, "orders"),
            Attributes.empty(),
            Attributes.of(MESSAGING_DESTINATION_NAME, "orders")),
        argumentSet(
            "template from start filters name from end",
            Attributes.of(MESSAGING_DESTINATION_TEMPLATE, "orders-{id}"),
            Attributes.of(MESSAGING_DESTINATION_NAME, "orders-42"),
            Attributes.of(MESSAGING_DESTINATION_TEMPLATE, "orders-{id}")),
        argumentSet(
            "temporary from end",
            Attributes.of(MESSAGING_DESTINATION_NAME, "temporary-42"),
            Attributes.of(MESSAGING_DESTINATION_TEMPORARY, true),
            Attributes.empty()),
        argumentSet(
            "anonymous from start",
            Attributes.of(MESSAGING_DESTINATION_ANONYMOUS, true),
            Attributes.of(MESSAGING_DESTINATION_NAME, "anonymous-42"),
            Attributes.empty()),
        argumentSet(
            "end overrides temporary flag and destination name",
            Attributes.of(
                MESSAGING_DESTINATION_TEMPORARY, true, MESSAGING_DESTINATION_NAME, "temporary"),
            Attributes.of(
                MESSAGING_DESTINATION_TEMPORARY, false, MESSAGING_DESTINATION_NAME, "orders"),
            Attributes.of(MESSAGING_DESTINATION_NAME, "orders")),
        argumentSet(
            "end overrides anonymous flag",
            Attributes.of(
                MESSAGING_DESTINATION_ANONYMOUS, true, MESSAGING_DESTINATION_NAME, "orders"),
            Attributes.of(MESSAGING_DESTINATION_ANONYMOUS, false),
            Attributes.of(MESSAGING_DESTINATION_NAME, "orders")));
  }

  @Test
  void passesUnchangedContextAndBatchCountWithoutMakingContextCurrent() {
    LongCounter counter = mock(LongCounter.class);
    MessagingConsumedMessagesRecorder recorder = new MessagingConsumedMessagesRecorder(counter);
    ContextKey<String> key = ContextKey.named("application");
    Context supplied =
        MessagingTelemetryState.add(
            Context.root().with(key, "supplied"), RECEIVE, CONSUMED_MESSAGES);
    Context ambient = Context.root().with(key, "ambient");
    Attributes start = Attributes.of(MESSAGING_BATCH_MESSAGE_COUNT, 5L);
    Attributes end = Attributes.of(MESSAGING_OPERATION_TYPE, "process", ERROR_TYPE, "failure");
    Attributes merged = start.toBuilder().putAll(end).build();
    doAnswer(
            invocation -> {
              assertThat(Context.current()).isSameAs(ambient);
              assertThat(invocation.<Context>getArgument(2)).isSameAs(supplied);
              return null;
            })
        .when(counter)
        .add(2, merged, supplied);

    try (Scope ignored = ambient.makeCurrent()) {
      assertThat(recorder.record(2, start, end, supplied)).isTrue();
      assertThat(Context.current()).isSameAs(ambient);
    }
    verify(counter).add(2, merged, supplied);
    assertThat(start).isEqualTo(Attributes.of(MESSAGING_BATCH_MESSAGE_COUNT, 5L));
    assertThat(end)
        .isEqualTo(Attributes.of(MESSAGING_OPERATION_TYPE, "process", ERROR_TYPE, "failure"));
  }

  @Test
  void explicitRecordingIgnoresAmbientConsumedSignalWhileListenerStillHonorsIt() {
    InMemoryMetricReader reader = InMemoryMetricReader.createDelta();
    SdkMeterProvider provider = SdkMeterProvider.builder().registerMetricReader(reader).build();
    cleanup.deferCleanup(provider);
    Meter meter = provider.get("test");
    MessagingConsumedMessagesRecorder recorder = MessagingConsumedMessagesRecorder.create(meter);
    OperationListener listener = MessagingConsumerMetrics.getConsumedMessages().create(meter);
    Context ambient = MessagingTelemetryState.add(Context.root(), RECEIVE, CONSUMED_MESSAGES);
    Attributes attributes = Attributes.of(MESSAGING_BATCH_MESSAGE_COUNT, 5L);

    try (Scope ignored = ambient.makeCurrent()) {
      Context context = listener.onStart(ambient, attributes, 100);
      listener.onEnd(context, Attributes.empty(), 200);
      assertThat(recorder.record(2, attributes, Attributes.empty(), ambient))
          .isEqualTo(emitStableMessagingSemconv());
      assertThat(Context.current()).isSameAs(ambient);
    }

    Collection<MetricData> metrics = reader.collectAllMetrics();
    if (emitStableMessagingSemconv()) {
      assertThat(metrics)
          .satisfiesExactly(
              metric ->
                  assertThat(metric)
                      .hasName("messaging.client.consumed.messages")
                      .hasLongSumSatisfying(
                          sum -> sum.hasPointsSatisfying(point -> point.hasValue(2))));
    } else {
      assertThat(metrics).isEmpty();
    }
  }

  @Test
  void ordinaryListenerAndDirectRecordingUseSameInstrument() {
    InMemoryMetricReader reader = InMemoryMetricReader.createDelta();
    SdkMeterProvider provider = SdkMeterProvider.builder().registerMetricReader(reader).build();
    cleanup.deferCleanup(provider);
    Meter meter = provider.get("test");
    MessagingConsumedMessagesRecorder recorder = MessagingConsumedMessagesRecorder.create(meter);
    OperationListener listener = MessagingConsumerMetrics.getConsumedMessages().create(meter);
    Attributes attributes =
        Attributes.of(MESSAGING_BATCH_MESSAGE_COUNT, 5L, MESSAGING_OPERATION_TYPE, "receive");

    Context context = listener.onStart(Context.root(), attributes, 100);
    listener.onEnd(context, Attributes.empty(), 200);
    assertThat(recorder.record(2, attributes, Attributes.empty(), Context.root()))
        .isEqualTo(emitStableMessagingSemconv());

    Collection<MetricData> metrics = reader.collectAllMetrics();
    if (emitStableMessagingSemconv()) {
      assertThat(metrics)
          .satisfiesExactly(
              metric ->
                  assertThat(metric)
                      .hasName("messaging.client.consumed.messages")
                      .hasLongSumSatisfying(
                          sum ->
                              sum.hasPointsSatisfying(
                                  point -> point.hasValue(7).hasAttributes(Attributes.empty()))));
    } else {
      assertThat(metrics).isEmpty();
    }
  }

  @Test
  void zeroDoesNotRecord() {
    LongCounter counter = mock(LongCounter.class);
    MessagingConsumedMessagesRecorder recorder = new MessagingConsumedMessagesRecorder(counter);

    assertThat(recorder.record(0, Attributes.empty(), Attributes.empty(), Context.root()))
        .isFalse();

    verifyNoInteractions(counter);
  }

  @ParameterizedTest
  @ValueSource(longs = {-1, Long.MIN_VALUE})
  void negativeCountIsInvalidEvenWhenDisabled(long count) {
    LongCounter counter = mock(LongCounter.class);
    MessagingConsumedMessagesRecorder recorder = new MessagingConsumedMessagesRecorder(counter);
    MessagingConsumedMessagesRecorder disabled = new MessagingConsumedMessagesRecorder(null);

    assertThatThrownBy(
            () -> recorder.record(count, Attributes.empty(), Attributes.empty(), Context.root()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> disabled.record(count, Attributes.empty(), Attributes.empty(), Context.root()))
        .isInstanceOf(IllegalArgumentException.class);

    verifyNoInteractions(counter);
  }

  @Test
  void disabledDoesNotRecord() {
    MessagingConsumedMessagesRecorder recorder = new MessagingConsumedMessagesRecorder(null);

    assertThat(recorder.record(2, Attributes.empty(), Attributes.empty(), Context.root()))
        .isFalse();
    assertThat(recorder.record(0, Attributes.empty(), Attributes.empty(), Context.root()))
        .isFalse();
  }

  @Test
  void acceptsNoopMeter() {
    MessagingConsumedMessagesRecorder recorder =
        MessagingConsumedMessagesRecorder.create(MeterProvider.noop().get("test"));

    assertThat(recorder.record(2, Attributes.empty(), Attributes.empty(), Context.root()))
        .isEqualTo(emitStableMessagingSemconv());
    assertThat(recorder.record(0, Attributes.empty(), Attributes.empty(), Context.root()))
        .isFalse();
    assertThatThrownBy(
            () -> recorder.record(-1, Attributes.empty(), Attributes.empty(), Context.root()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void incompatibleMeterUsesExistingWarningAndDoesNotBuildCounter() {
    Meter meter = mock(Meter.class);
    DoubleHistogramBuilder builder = mock(DoubleHistogramBuilder.class);
    when(meter.histogramBuilder("compatibility-test")).thenReturn(builder);
    List<LogRecord> warnings = new ArrayList<>();
    Handler handler =
        new Handler() {
          @Override
          public void publish(LogRecord record) {
            warnings.add(record);
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    Logger logger = Logger.getLogger(OperationMetricsUtil.class.getName());
    logger.addHandler(handler);
    cleanup.deferCleanup(() -> logger.removeHandler(handler));

    MessagingConsumedMessagesRecorder recorder = MessagingConsumedMessagesRecorder.create(meter);
    assertThat(recorder.record(2, Attributes.empty(), Attributes.empty(), Context.root()))
        .isFalse();

    verify(meter, never()).counterBuilder(anyString());
    if (emitStableMessagingSemconv()) {
      assertThat(warnings)
          .satisfiesExactly(
              warning -> {
                assertThat(warning.getLevel()).isEqualTo(WARNING);
                assertThat(warning.getMessage())
                    .isEqualTo(
                        "Disabling {0} metrics because {1} does not implement {2}. This prevents using "
                            + "metrics advice, which could result in {0} metrics having high cardinality "
                            + "attributes.");
                assertThat(warning.getParameters())
                    .containsExactly(
                        "messaging consumed messages",
                        builder.getClass().getName(),
                        ExtendedDoubleHistogramBuilder.class.getName());
              });
    } else {
      verifyNoInteractions(meter);
      assertThat(warnings).isEmpty();
    }
  }

  @Test
  void recordingFailurePropagatesWithoutChangingCurrentContext() {
    LongCounter counter = mock(LongCounter.class);
    MessagingConsumedMessagesRecorder recorder = new MessagingConsumedMessagesRecorder(counter);
    Context context = Context.root().with(ContextKey.named("application"), "supplied");
    Context ambient = Context.current();
    IllegalStateException failure = new IllegalStateException("recording failed");
    doThrow(failure).when(counter).add(2, Attributes.empty(), context);

    assertThatThrownBy(() -> recorder.record(2, Attributes.empty(), Attributes.empty(), context))
        .isSameAs(failure);
    assertThat(Context.current()).isSameAs(ambient);
  }
}
