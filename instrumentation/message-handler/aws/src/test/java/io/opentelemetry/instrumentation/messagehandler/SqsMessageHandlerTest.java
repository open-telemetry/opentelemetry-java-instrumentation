/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.messagehandler;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.testing.assertj.TracesAssert;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

public class SqsMessageHandlerTest {
  private static final OpenTelemetrySdk openTelemetry;
  private static final InMemorySpanExporter testSpanExporter;

  static {
    testSpanExporter = InMemorySpanExporter.create();
    InMemoryMetricExporter testMetricExporter =
        InMemoryMetricExporter.create(AggregationTemporality.DELTA);

    MetricReader metricReader =
        PeriodicMetricReader.builder(testMetricExporter)
            // Set really long interval. We'll call forceFlush when we need the metrics
            // instead of collecting them periodically.
            .setInterval(Duration.ofNanos(Long.MAX_VALUE))
            .build();

    openTelemetry =
        OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(new FlushTrackingSpanProcessor())
                    .addSpanProcessor(SimpleSpanProcessor.create(testSpanExporter))
                    .build())
            .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(metricReader).build())
            .setPropagators(ContextPropagators.create(AwsXrayPropagator.getInstance()))
            .buildAndRegisterGlobal();
  }

  private static class FlushTrackingSpanProcessor implements SpanProcessor {
    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {}

    @Override
    public boolean isStartRequired() {
      return false;
    }

    @Override
    public void onEnd(ReadableSpan span) {}

    @Override
    public boolean isEndRequired() {
      return false;
    }

    @Override
    public CompletableResultCode forceFlush() {
      return CompletableResultCode.ofSuccess();
    }
  }

  @SafeVarargs
  @SuppressWarnings("varargs")
  public final void waitAndAssertTraces(Consumer<TraceAssert>... assertions) {
    waitAndAssertTraces(null, Arrays.asList(assertions), true);
  }

  private <T extends Consumer<TraceAssert>> void waitAndAssertTraces(
      @Nullable Comparator<List<SpanData>> traceComparator,
      Iterable<T> assertions,
      boolean verifyScopeVersion) {
    List<Consumer<TraceAssert>> assertionsList = new ArrayList<>();
    assertions.forEach(assertionsList::add);

    try {
      await()
          .untilAsserted(() -> doAssertTraces(traceComparator, assertionsList, verifyScopeVersion));
    } catch (ConditionTimeoutException e) {
      // Don't throw this failure since the stack is the awaitility thread, causing confusion.
      // Instead, just assert one more time on the test thread, which will fail with a better stack
      // trace.
      // TODO(anuraaga): There is probably a better way to do this.
      doAssertTraces(traceComparator, assertionsList, verifyScopeVersion);
    }
  }

  private void doAssertTraces(
      @Nullable Comparator<List<SpanData>> traceComparator,
      List<Consumer<TraceAssert>> assertionsList,
      boolean verifyScopeVersion) {
    List<List<SpanData>> traces = waitForTraces(assertionsList.size());
    if (verifyScopeVersion) {
      TelemetryDataUtil.assertScopeVersion(traces);
    }
    if (traceComparator != null) {
      traces.sort(traceComparator);
    }
    TracesAssert.assertThat(traces).hasTracesSatisfyingExactly(assertionsList);
  }

  public final List<List<SpanData>> waitForTraces(int numberOfTraces) {
    try {
      return TelemetryDataUtil.waitForTraces(
          this::getExportedSpans, numberOfTraces, 20, TimeUnit.SECONDS);
    } catch (TimeoutException | InterruptedException e) {
      throw new AssertionError("Error waiting for " + numberOfTraces + " traces", e);
    }
  }

  public List<SpanData> getExportedSpans() {
    return testSpanExporter.getFinishedSpanItems();
  }

  @AfterEach
  public void resetTests() {
    testSpanExporter.reset();
  }

  @Test
  public void simple() {
    Message sqsMessage =
        Message.builder()
            .attributesWithStrings(
                Collections.singletonMap(
                    "AWSTraceHeader",
                    "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=1"))
            .build();

    AtomicInteger counter = new AtomicInteger(0);

    SqsMessageHandler messageHandler =
        new SqsMessageHandler(openTelemetry, messages -> "Batch of Messages") {
          @Override
          protected void doHandleMessages(Collection<Message> messages) {
            counter.getAndIncrement();
          }
        };

    Span parentSpan = openTelemetry.getTracer("test").spanBuilder("test").startSpan();

    try (Scope scope = parentSpan.makeCurrent()) {
      messageHandler.handleMessages(Collections.singletonList(sqsMessage));
    }

    parentSpan.end();

    waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("Batch of Messages")
                        .hasKind(SpanKind.CONSUMER)
                        .hasLinks(
                            LinkData.create(
                                SpanContext.createFromRemoteParent(
                                    "55555555123456789012345678901234",
                                    "1234567890123456",
                                    TraceFlags.getSampled(),
                                    TraceState.getDefault())))
                        .hasTotalRecordedLinks(1)
                        .hasAttribute(
                            SemanticAttributes.MESSAGING_OPERATION, MessageOperation.RECEIVE.name())
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasTotalAttributeCount(2)
                        .hasParentSpanId(parentSpan.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan.getSpanContext().getTraceId())));

    Assert.assertEquals(1, counter.get());
  }

  @Test
  public void simpleMessage() {
    Message sqsMessage =
        Message.builder()
            .messageAttributes(
                Collections.singletonMap(
                    "X-Amzn-Trace-Id",
                    MessageAttributeValue.builder()
                        .stringValue(
                            "Root=1-66555555-123456789012345678901234;Parent=2234567890123456;Sampled=1")
                        .build()))
            .build();

    AtomicInteger counter = new AtomicInteger(0);

    SqsMessageHandler messageHandler =
        new SqsMessageHandler(openTelemetry, messages -> "Batch of Messages") {
          @Override
          protected void doHandleMessages(Collection<Message> messages) {
            counter.getAndIncrement();
          }
        };

    Span parentSpan = openTelemetry.getTracer("test").spanBuilder("test").startSpan();

    try (Scope scope = parentSpan.makeCurrent()) {
      messageHandler.handleMessages(Collections.singletonList(sqsMessage));
    }

    parentSpan.end();

    waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("Batch of Messages")
                        .hasKind(SpanKind.CONSUMER)
                        .hasLinks(
                            LinkData.create(
                                SpanContext.createFromRemoteParent(
                                    "66555555123456789012345678901234",
                                    "2234567890123456",
                                    TraceFlags.getSampled(),
                                    TraceState.getDefault())))
                        .hasTotalRecordedLinks(1)
                        .hasAttribute(
                            SemanticAttributes.MESSAGING_OPERATION, MessageOperation.RECEIVE.name())
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasTotalAttributeCount(2)
                        .hasParentSpanId(parentSpan.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan.getSpanContext().getTraceId())));

    Assert.assertEquals(1, counter.get());
  }

  @Test
  public void multipleMessages() {
    List<Message> sqsMessages = new LinkedList<>();

    Message sqsMessage1 =
        Message.builder()
            .attributesWithStrings(
                Collections.singletonMap(
                    "AWSTraceHeader",
                    "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=1"))
            .build();
    sqsMessages.add(sqsMessage1);

    Message sqsMessage2 =
        Message.builder()
            .attributesWithStrings(
                Collections.singletonMap(
                    "AWSTraceHeader",
                    "Root=1-44444444-123456789012345678901234;Parent=2481624816248161;Sampled=0"))
            .build();
    sqsMessages.add(sqsMessage2);

    AtomicInteger counter = new AtomicInteger(0);

    SqsMessageHandler messageHandler =
        new SqsMessageHandler(openTelemetry, messages -> "Batch of Messages") {
          @Override
          protected void doHandleMessages(Collection<Message> messages) {
            counter.getAndIncrement();
          }
        };

    Span parentSpan = openTelemetry.getTracer("test").spanBuilder("test").startSpan();

    try (Scope scope = parentSpan.makeCurrent()) {
      messageHandler.handleMessages(sqsMessages);
    }

    parentSpan.end();

    waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("Batch of Messages")
                        .hasKind(SpanKind.CONSUMER)
                        .hasLinks(
                            LinkData.create(
                                SpanContext.createFromRemoteParent(
                                    "55555555123456789012345678901234",
                                    "1234567890123456",
                                    TraceFlags.getSampled(),
                                    TraceState.getDefault())),
                            LinkData.create(
                                SpanContext.createFromRemoteParent(
                                    "44444444123456789012345678901234",
                                    "2481624816248161",
                                    TraceFlags.getDefault(),
                                    TraceState.getDefault())))
                        .hasTotalRecordedLinks(2)
                        .hasAttribute(
                            SemanticAttributes.MESSAGING_OPERATION, MessageOperation.RECEIVE.name())
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasTotalAttributeCount(2)
                        .hasParentSpanId(parentSpan.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan.getSpanContext().getTraceId())));

    Assert.assertEquals(1, counter.get());
  }

  @Test
  public void multipleRunsOfTheHandler() {
    Message sqsMessage1 =
        Message.builder()
            .attributesWithStrings(
                Collections.singletonMap(
                    "AWSTraceHeader",
                    "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=1"))
            .build();

    Message sqsMessage2 =
        Message.builder()
            .attributesWithStrings(
                Collections.singletonMap(
                    "AWSTraceHeader",
                    "Root=1-44444444-123456789012345678901234;Parent=2481624816248161;Sampled=0"))
            .build();

    AtomicInteger counter = new AtomicInteger(0);

    SqsMessageHandler messageHandler =
        new SqsMessageHandler(openTelemetry, messages -> "Batch of Messages") {
          @Override
          protected void doHandleMessages(Collection<Message> messages) {
            counter.getAndIncrement();
          }
        };

    Span parentSpan = openTelemetry.getTracer("test").spanBuilder("test").startSpan();

    try (Scope scope = parentSpan.makeCurrent()) {
      messageHandler.handleMessages(Collections.singletonList(sqsMessage1));
      messageHandler.handleMessages(Collections.singletonList(sqsMessage2));
    }

    parentSpan.end();

    waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("Batch of Messages")
                        .hasKind(SpanKind.CONSUMER)
                        .hasLinks(
                            LinkData.create(
                                SpanContext.createFromRemoteParent(
                                    "55555555123456789012345678901234",
                                    "1234567890123456",
                                    TraceFlags.getSampled(),
                                    TraceState.getDefault())))
                        .hasTotalRecordedLinks(1)
                        .hasAttribute(
                            SemanticAttributes.MESSAGING_OPERATION, MessageOperation.RECEIVE.name())
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasTotalAttributeCount(2)
                        .hasParentSpanId(parentSpan.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan.getSpanContext().getTraceId()),
                span ->
                    span.hasName("Batch of Messages")
                        .hasKind(SpanKind.CONSUMER)
                        .hasLinks(
                            LinkData.create(
                                SpanContext.createFromRemoteParent(
                                    "44444444123456789012345678901234",
                                    "2481624816248161",
                                    TraceFlags.getDefault(),
                                    TraceState.getDefault())))
                        .hasTotalRecordedLinks(1)
                        .hasAttribute(
                            SemanticAttributes.MESSAGING_OPERATION, MessageOperation.RECEIVE.name())
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasTotalAttributeCount(2)
                        .hasParentSpanId(parentSpan.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan.getSpanContext().getTraceId())));

    Assert.assertEquals(2, counter.get());
  }

  @Test
  public void noMessages() {
    AtomicInteger counter = new AtomicInteger(0);

    SqsMessageHandler messageHandler =
        new SqsMessageHandler(openTelemetry, messages -> "Batch of Messages") {
          @Override
          protected void doHandleMessages(Collection<Message> messages) {
            counter.getAndIncrement();
          }
        };

    Span parentSpan = openTelemetry.getTracer("test").spanBuilder("test").startSpan();

    try (Scope scope = parentSpan.makeCurrent()) {
      messageHandler.handleMessages(Collections.emptyList());
    }

    parentSpan.end();

    waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("Batch of Messages")
                        .hasKind(SpanKind.CONSUMER)
                        .hasTotalRecordedLinks(0)
                        .hasAttribute(
                            SemanticAttributes.MESSAGING_OPERATION, MessageOperation.RECEIVE.name())
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasTotalAttributeCount(2)
                        .hasParentSpanId(parentSpan.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan.getSpanContext().getTraceId())));

    Assert.assertEquals(1, counter.get());
  }

  @Test
  public void changeDefaults() {
    Message sqsMessage =
        Message.builder()
            .attributesWithStrings(
                Collections.singletonMap(
                    "AWSTraceHeader",
                    "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=1"))
            .build();

    AtomicInteger counter = new AtomicInteger(0);

    SqsMessageHandler messageHandler =
        new SqsMessageHandler(openTelemetry, messages -> "New Name", MessageOperation.PROCESS) {
          @Override
          protected void doHandleMessages(Collection<Message> messages) {
            counter.getAndIncrement();
          }
        };

    Span parentSpan = openTelemetry.getTracer("test").spanBuilder("test").startSpan();

    try (Scope scope = parentSpan.makeCurrent()) {
      messageHandler.handleMessages(Collections.singletonList(sqsMessage));
    }

    parentSpan.end();

    waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("New Name")
                        .hasKind(SpanKind.CONSUMER)
                        .hasLinks(
                            LinkData.create(
                                SpanContext.createFromRemoteParent(
                                    "55555555123456789012345678901234",
                                    "1234567890123456",
                                    TraceFlags.getSampled(),
                                    TraceState.getDefault())))
                        .hasTotalRecordedLinks(1)
                        .hasAttribute(
                            SemanticAttributes.MESSAGING_OPERATION, MessageOperation.PROCESS.name())
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasTotalAttributeCount(2)
                        .hasParentSpanId(parentSpan.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan.getSpanContext().getTraceId())));

    Assert.assertEquals(1, counter.get());
  }

  @Test
  public void testSender() {
    Message sqsMessage =
        Message.builder()
            .attributesWithStrings(
                Collections.singletonMap(
                    "AWSTraceHeader",
                    "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=1"))
            .build();

    AtomicInteger counter = new AtomicInteger(0);

    SqsMessageHandler messageHandler =
        new SqsMessageHandler(openTelemetry, messages -> "New Name", MessageOperation.SEND) {
          @Override
          protected void doHandleMessages(Collection<Message> messages) {
            counter.getAndIncrement();
          }
        };

    Span parentSpan = openTelemetry.getTracer("test").spanBuilder("test").startSpan();

    try (Scope scope = parentSpan.makeCurrent()) {
      messageHandler.handleMessages(Collections.singletonList(sqsMessage));
    }

    parentSpan.end();

    waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("New Name")
                        .hasKind(SpanKind.PRODUCER)
                        .hasLinks(
                            LinkData.create(
                                SpanContext.createFromRemoteParent(
                                    "55555555123456789012345678901234",
                                    "1234567890123456",
                                    TraceFlags.getSampled(),
                                    TraceState.getDefault())))
                        .hasTotalRecordedLinks(1)
                        .hasAttribute(
                            SemanticAttributes.MESSAGING_OPERATION, MessageOperation.SEND.name())
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasTotalAttributeCount(2)
                        .hasParentSpanId(parentSpan.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan.getSpanContext().getTraceId())));

    Assert.assertEquals(1, counter.get());
  }

  @Test
  public void invalidUpstreamParent() {
    Message sqsMessage =
        Message.builder()
            .attributesWithStrings(
                Collections.singletonMap(
                    "AWSTraceHeader", "Root=1-55555555-invalid;Parent=1234567890123456;Sampled=1"))
            .build();

    AtomicInteger counter = new AtomicInteger(0);

    SqsMessageHandler messageHandler =
        new SqsMessageHandler(openTelemetry, messages -> "Batch of Messages") {
          @Override
          protected void doHandleMessages(Collection<Message> messages) {
            counter.getAndIncrement();
          }
        };

    Span parentSpan = openTelemetry.getTracer("test").spanBuilder("test").startSpan();

    assertThrows(
        StringIndexOutOfBoundsException.class,
        () -> {
          try (Scope scope = parentSpan.makeCurrent()) {
            messageHandler.handleMessages(Collections.singletonList(sqsMessage));
          }
        });
  }

  @Test
  public void exceptionInHandle() {
    Message sqsMessage =
        Message.builder()
            .attributesWithStrings(
                Collections.singletonMap(
                    "AWSTraceHeader",
                    "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=1"))
            .build();

    AtomicInteger counter = new AtomicInteger(0);

    SqsMessageHandler messageHandler =
        new SqsMessageHandler(openTelemetry, messages -> "Batch of Messages") {
          @Override
          protected void doHandleMessages(Collection<Message> messages) {
            counter.getAndIncrement();
            throw new RuntimeException("Injected Error");
          }
        };

    Span parentSpan = openTelemetry.getTracer("test").spanBuilder("test").startSpan();

    assertThrows(
        RuntimeException.class,
        () -> {
          try (Scope scope = parentSpan.makeCurrent()) {
            messageHandler.handleMessages(Collections.singletonList(sqsMessage));
          }
        });

    parentSpan.end();

    waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("Batch of Messages")
                        .hasKind(SpanKind.CONSUMER)
                        .hasLinks(
                            LinkData.create(
                                SpanContext.createFromRemoteParent(
                                    "55555555123456789012345678901234",
                                    "1234567890123456",
                                    TraceFlags.getSampled(),
                                    TraceState.getDefault())))
                        .hasTotalRecordedLinks(1)
                        .hasException(new RuntimeException("Injected Error"))
                        .hasAttribute(
                            SemanticAttributes.MESSAGING_OPERATION, MessageOperation.RECEIVE.name())
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasTotalAttributeCount(2)
                        .hasParentSpanId(parentSpan.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan.getSpanContext().getTraceId())));

    Assert.assertEquals(1, counter.get());
  }
}
