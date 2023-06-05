/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.messagehandler;

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

public class SqsBatchMessageHandlerTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  static {
    // Change to the X-Ray propagator
    try {
      Field field = OpenTelemetrySdk.class.getDeclaredField("propagators");
      field.setAccessible(true);
      field.set(
          testing.getOpenTelemetrySdk(),
          ContextPropagators.create(AwsXrayPropagator.getInstance()));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void simple() {
    Message sqsMessage =
        Message.builder()
            .attributesWithStrings(Collections.singletonMap("AWSTraceHeader", "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=1"))
            .build();

    AtomicInteger counter = new AtomicInteger(0);

    SqsBatchMessageHandler messageHandler =
        new SqsBatchMessageHandler(testing.getOpenTelemetrySdk(), messages -> "Batch of Messages") {
          @Override
          protected void doHandleMessages(Collection<Message> messages) {
            counter.getAndIncrement();
          }
        };

    Span parentSpan =
        testing.getOpenTelemetrySdk().getTracer("test").spanBuilder("test").startSpan();

    try (Scope scope = parentSpan.makeCurrent()) {
      messageHandler.handleMessages(Collections.singletonList(sqsMessage));
    }

    parentSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("Batch of Messages")
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
            .messageAttributes(Collections.singletonMap("X-Amzn-Trace-Id", MessageAttributeValue.builder().stringValue("Root=1-66555555-123456789012345678901234;Parent=2234567890123456;Sampled=1").build()))
            .build();

    AtomicInteger counter = new AtomicInteger(0);

    SqsBatchMessageHandler messageHandler =
        new SqsBatchMessageHandler(testing.getOpenTelemetrySdk(), messages -> "Batch of Messages") {
          @Override
          protected void doHandleMessages(Collection<Message> messages) {
            counter.getAndIncrement();
          }
        };

    Span parentSpan =
        testing.getOpenTelemetrySdk().getTracer("test").spanBuilder("test").startSpan();

    try (Scope scope = parentSpan.makeCurrent()) {
      messageHandler.handleMessages(Collections.singletonList(sqsMessage));
    }

    parentSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("Batch of Messages")
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
            .attributesWithStrings(Collections.singletonMap("AWSTraceHeader", "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=1"))
            .build();
    sqsMessages.add(sqsMessage1);

    Message sqsMessage2 =
        Message.builder()
            .attributesWithStrings(Collections.singletonMap("AWSTraceHeader", "Root=1-44444444-123456789012345678901234;Parent=2481624816248161;Sampled=0"))
            .build();
    sqsMessages.add(sqsMessage2);

    AtomicInteger counter = new AtomicInteger(0);

    SqsBatchMessageHandler messageHandler =
        new SqsBatchMessageHandler(testing.getOpenTelemetrySdk(), messages -> "Batch of Messages") {
          @Override
          protected void doHandleMessages(Collection<Message> messages) {
            counter.getAndIncrement();
          }
        };

    Span parentSpan =
        testing.getOpenTelemetrySdk().getTracer("test").spanBuilder("test").startSpan();

    try (Scope scope = parentSpan.makeCurrent()) {
      messageHandler.handleMessages(sqsMessages);
    }

    parentSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("Batch of Messages")
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
            .attributesWithStrings(Collections.singletonMap("AWSTraceHeader", "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=1"))
            .build();

    Message sqsMessage2 =
        Message.builder()
            .attributesWithStrings(Collections.singletonMap("AWSTraceHeader", "Root=1-44444444-123456789012345678901234;Parent=2481624816248161;Sampled=0"))
            .build();

    AtomicInteger counter = new AtomicInteger(0);

    SqsBatchMessageHandler messageHandler =
        new SqsBatchMessageHandler(
            testing.getOpenTelemetrySdk(),
            messages -> "Batch of Messages",
            MessageOperation.PROCESS.name()) {
          @Override
          protected void doHandleMessages(Collection<Message> messages) {
            counter.getAndIncrement();
          }
        };

    Span parentSpan =
        testing.getOpenTelemetrySdk().getTracer("test").spanBuilder("test").startSpan();

    try (Scope scope = parentSpan.makeCurrent()) {
      messageHandler.handleMessages(Collections.singletonList(sqsMessage1));
      messageHandler.handleMessages(Collections.singletonList(sqsMessage2));
    }

    parentSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("Batch of Messages")
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
                        .hasTraceId(parentSpan.getSpanContext().getTraceId()),
                span ->
                    span.hasName("Batch of Messages")
                        .hasLinks(
                            LinkData.create(
                                SpanContext.createFromRemoteParent(
                                    "44444444123456789012345678901234",
                                    "2481624816248161",
                                    TraceFlags.getDefault(),
                                    TraceState.getDefault())))
                        .hasTotalRecordedLinks(1)
                        .hasAttribute(
                            SemanticAttributes.MESSAGING_OPERATION, MessageOperation.PROCESS.name())
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasTotalAttributeCount(2)
                        .hasParentSpanId(parentSpan.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan.getSpanContext().getTraceId())));

    Assert.assertEquals(2, counter.get());
  }

  @Test
  public void noMessages() {
    AtomicInteger counter = new AtomicInteger(0);

    SqsBatchMessageHandler messageHandler =
        new SqsBatchMessageHandler(testing.getOpenTelemetrySdk(), messages -> "Batch of Messages") {
          @Override
          protected void doHandleMessages(Collection<Message> messages) {
            counter.getAndIncrement();
          }
        };

    Span parentSpan =
        testing.getOpenTelemetrySdk().getTracer("test").spanBuilder("test").startSpan();

    try (Scope scope = parentSpan.makeCurrent()) {
      messageHandler.handleMessages(Collections.emptyList());
    }

    parentSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("Batch of Messages")
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
            .attributesWithStrings(Collections.singletonMap("AWSTraceHeader", "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=1"))
            .build();

    AtomicInteger counter = new AtomicInteger(0);

    SqsBatchMessageHandler messageHandler =
        new SqsBatchMessageHandler(
            testing.getOpenTelemetrySdk(),
            messages -> "New Name",
            MessageOperation.PROCESS.name()) {
          @Override
          protected void doHandleMessages(Collection<Message> messages) {
            counter.getAndIncrement();
          }
        };

    Span parentSpan =
        testing.getOpenTelemetrySdk().getTracer("test").spanBuilder("test").startSpan();

    try (Scope scope = parentSpan.makeCurrent()) {
      messageHandler.handleMessages(Collections.singletonList(sqsMessage));
    }

    parentSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("New Name")
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
  public void invalidUpstreamParent() {
    Message sqsMessage =
        Message.builder()
            .attributesWithStrings(Collections.singletonMap("AWSTraceHeader", "Root=1-55555555-invalid;Parent=1234567890123456;Sampled=1"))
            .build();

    AtomicInteger counter = new AtomicInteger(0);

    SqsBatchMessageHandler messageHandler =
        new SqsBatchMessageHandler(testing.getOpenTelemetrySdk(), messages -> "Batch of Messages") {
          @Override
          protected void doHandleMessages(Collection<Message> messages) {
            counter.getAndIncrement();
          }
        };

    Span parentSpan =
        testing.getOpenTelemetrySdk().getTracer("test").spanBuilder("test").startSpan();

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
            .attributesWithStrings(Collections.singletonMap("AWSTraceHeader", "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=1"))
            .build();

    AtomicInteger counter = new AtomicInteger(0);

    SqsBatchMessageHandler messageHandler =
        new SqsBatchMessageHandler(testing.getOpenTelemetrySdk(), messages -> "Batch of Messages") {
          @Override
          protected void doHandleMessages(Collection<Message> messages) {
            counter.getAndIncrement();
            throw new RuntimeException("Injected Error");
          }
        };

    Span parentSpan =
        testing.getOpenTelemetrySdk().getTracer("test").spanBuilder("test").startSpan();

    assertThrows(
        RuntimeException.class,
        () -> {
          try (Scope scope = parentSpan.makeCurrent()) {
            messageHandler.handleMessages(Collections.singletonList(sqsMessage));
          }
        });

    parentSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("Batch of Messages")
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
