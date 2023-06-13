/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

public class SqsMessageHandlerTest extends XrayTestInstrumenter {
  @Test
  public void simple() {
    Message sqsMessage =
        Message.builder()
            .body("Hello")
            .attributesWithStrings(
                Collections.singletonMap(
                    "AWSTraceHeader",
                    "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=1"))
            .build();

    AtomicInteger counter = new AtomicInteger(0);

    SqsMessageHandler messageHandler =
        new SqsMessageHandler(getOpenTelemetry(), "destination", MessageOperation.RECEIVE) {
          @Override
          protected void doHandleMessages(Collection<Message> messages) {
            counter.getAndIncrement();
          }
        };

    Span parentSpan = getOpenTelemetry().getTracer("test").spanBuilder("test").startSpan();

    try (Scope scope = parentSpan.makeCurrent()) {
      messageHandler.handleMessages(Collections.singletonList(sqsMessage));
    }

    parentSpan.end();

    waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("destination receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasLinks(
                            LinkData.create(
                                SpanContext.createFromRemoteParent(
                                    "55555555123456789012345678901234",
                                    "1234567890123456",
                                    TraceFlags.getSampled(),
                                    TraceState.getDefault())))
                        .hasTotalRecordedLinks(1)
                        .hasAttribute(SemanticAttributes.MESSAGING_OPERATION, "receive")
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, "destination")
                        .hasAttribute(SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, 5L)
                        .hasTotalAttributeCount(4)
                        .hasParentSpanId(parentSpan.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan.getSpanContext().getTraceId())));

    Assert.assertEquals(1, counter.get());
  }

  @Test
  public void simpleMessage() {
    Message sqsMessage =
        Message.builder()
            .body("Hello")
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
        new SqsMessageHandler(getOpenTelemetry(), "destination", MessageOperation.RECEIVE) {
          @Override
          protected void doHandleMessages(Collection<Message> messages) {
            counter.getAndIncrement();
          }
        };

    Span parentSpan = getOpenTelemetry().getTracer("test").spanBuilder("test").startSpan();

    try (Scope scope = parentSpan.makeCurrent()) {
      messageHandler.handleMessages(Collections.singletonList(sqsMessage));
    }

    parentSpan.end();

    waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("destination receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasLinks(
                            LinkData.create(
                                SpanContext.createFromRemoteParent(
                                    "66555555123456789012345678901234",
                                    "2234567890123456",
                                    TraceFlags.getSampled(),
                                    TraceState.getDefault())))
                        .hasTotalRecordedLinks(1)
                        .hasAttribute(SemanticAttributes.MESSAGING_OPERATION, "receive")
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, "destination")
                        .hasAttribute(SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, 5L)
                        .hasTotalAttributeCount(4)
                        .hasParentSpanId(parentSpan.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan.getSpanContext().getTraceId())));

    Assert.assertEquals(1, counter.get());
  }

  @Test
  public void multipleMessages() {
    List<Message> sqsMessages = new LinkedList<>();

    Message sqsMessage1 =
        Message.builder()
            .body("Hello")
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
        new SqsMessageHandler(getOpenTelemetry(), "destination", MessageOperation.RECEIVE) {
          @Override
          protected void doHandleMessages(Collection<Message> messages) {
            counter.getAndIncrement();
          }
        };

    Span parentSpan = getOpenTelemetry().getTracer("test").spanBuilder("test").startSpan();

    try (Scope scope = parentSpan.makeCurrent()) {
      messageHandler.handleMessages(sqsMessages);
    }

    parentSpan.end();

    waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("destination receive")
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
                        .hasAttribute(SemanticAttributes.MESSAGING_OPERATION, "receive")
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, "destination")
                        .hasAttribute(SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, 5L)
                        .hasTotalAttributeCount(4)
                        .hasParentSpanId(parentSpan.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan.getSpanContext().getTraceId())));

    Assert.assertEquals(1, counter.get());
  }

  @Test
  public void multipleRunsOfTheHandler() {
    Message sqsMessage1 =
        Message.builder()
            .body("Hello")
            .attributesWithStrings(
                Collections.singletonMap(
                    "AWSTraceHeader",
                    "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=1"))
            .build();

    Message sqsMessage2 =
        Message.builder()
            .body("Hello World")
            .attributesWithStrings(
                Collections.singletonMap(
                    "AWSTraceHeader",
                    "Root=1-44444444-123456789012345678901234;Parent=2481624816248161;Sampled=0"))
            .build();

    AtomicInteger counter = new AtomicInteger(0);

    SqsMessageHandler messageHandler =
        new SqsMessageHandler(getOpenTelemetry(), "destination", MessageOperation.RECEIVE) {
          @Override
          protected void doHandleMessages(Collection<Message> messages) {
            counter.getAndIncrement();
          }
        };

    Span parentSpan = getOpenTelemetry().getTracer("test").spanBuilder("test").startSpan();

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
                    span.hasName("destination receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasLinks(
                            LinkData.create(
                                SpanContext.createFromRemoteParent(
                                    "55555555123456789012345678901234",
                                    "1234567890123456",
                                    TraceFlags.getSampled(),
                                    TraceState.getDefault())))
                        .hasTotalRecordedLinks(1)
                        .hasAttribute(SemanticAttributes.MESSAGING_OPERATION, "receive")
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, "destination")
                        .hasAttribute(SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, 5L)
                        .hasTotalAttributeCount(4)
                        .hasParentSpanId(parentSpan.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan.getSpanContext().getTraceId()),
                span ->
                    span.hasName("destination receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasLinks(
                            LinkData.create(
                                SpanContext.createFromRemoteParent(
                                    "44444444123456789012345678901234",
                                    "2481624816248161",
                                    TraceFlags.getDefault(),
                                    TraceState.getDefault())))
                        .hasTotalRecordedLinks(1)
                        .hasAttribute(SemanticAttributes.MESSAGING_OPERATION, "receive")
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, "destination")
                        .hasAttribute(SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, 11L)
                        .hasTotalAttributeCount(4)
                        .hasParentSpanId(parentSpan.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan.getSpanContext().getTraceId())));

    Assert.assertEquals(2, counter.get());
  }

  @Test
  public void noMessages() {
    AtomicInteger counter = new AtomicInteger(0);

    SqsMessageHandler messageHandler =
        new SqsMessageHandler(getOpenTelemetry(), "destination", MessageOperation.RECEIVE) {
          @Override
          protected void doHandleMessages(Collection<Message> messages) {
            counter.getAndIncrement();
          }
        };

    Span parentSpan = getOpenTelemetry().getTracer("test").spanBuilder("test").startSpan();

    try (Scope scope = parentSpan.makeCurrent()) {
      messageHandler.handleMessages(Collections.emptyList());
    }

    parentSpan.end();

    waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("destination receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasTotalRecordedLinks(0)
                        .hasAttribute(SemanticAttributes.MESSAGING_OPERATION, "receive")
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, "destination")
                        .hasAttribute(SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, 0L)
                        .hasTotalAttributeCount(4)
                        .hasParentSpanId(parentSpan.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan.getSpanContext().getTraceId())));

    Assert.assertEquals(1, counter.get());
  }

  @Test
  public void changeDefaults() {
    Message sqsMessage =
        Message.builder()
            .body("Hello")
            .attributesWithStrings(
                Collections.singletonMap(
                    "AWSTraceHeader",
                    "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=1"))
            .build();

    AtomicInteger counter = new AtomicInteger(0);

    SqsMessageHandler messageHandler =
        new SqsMessageHandler(getOpenTelemetry(), "destination2", MessageOperation.PROCESS) {
          @Override
          protected void doHandleMessages(Collection<Message> messages) {
            counter.getAndIncrement();
          }
        };

    Span parentSpan = getOpenTelemetry().getTracer("test").spanBuilder("test").startSpan();

    try (Scope scope = parentSpan.makeCurrent()) {
      messageHandler.handleMessages(Collections.singletonList(sqsMessage));
    }

    parentSpan.end();

    waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("destination2 process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasLinks(
                            LinkData.create(
                                SpanContext.createFromRemoteParent(
                                    "55555555123456789012345678901234",
                                    "1234567890123456",
                                    TraceFlags.getSampled(),
                                    TraceState.getDefault())))
                        .hasTotalRecordedLinks(1)
                        .hasAttribute(SemanticAttributes.MESSAGING_OPERATION, "process")
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, "destination2")
                        .hasAttribute(SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, 5L)
                        .hasTotalAttributeCount(4)
                        .hasParentSpanId(parentSpan.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan.getSpanContext().getTraceId())));

    Assert.assertEquals(1, counter.get());
  }

  @Test
  public void testSender() {
    Message sqsMessage =
        Message.builder()
            .body("Hello")
            .attributesWithStrings(
                Collections.singletonMap(
                    "AWSTraceHeader",
                    "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=1"))
            .build();

    AtomicInteger counter = new AtomicInteger(0);

    SqsMessageHandler messageHandler =
        new SqsMessageHandler(getOpenTelemetry(), "destination3", MessageOperation.SEND) {
          @Override
          protected void doHandleMessages(Collection<Message> messages) {
            counter.getAndIncrement();
          }
        };

    Span parentSpan = getOpenTelemetry().getTracer("test").spanBuilder("test").startSpan();

    try (Scope scope = parentSpan.makeCurrent()) {
      messageHandler.handleMessages(Collections.singletonList(sqsMessage));
    }

    parentSpan.end();

    waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("destination3 send")
                        .hasKind(SpanKind.PRODUCER)
                        .hasLinks(
                            LinkData.create(
                                SpanContext.createFromRemoteParent(
                                    "55555555123456789012345678901234",
                                    "1234567890123456",
                                    TraceFlags.getSampled(),
                                    TraceState.getDefault())))
                        .hasTotalRecordedLinks(1)
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, "destination3")
                        .hasAttribute(SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, 5L)
                        .hasTotalAttributeCount(3)
                        .hasParentSpanId(parentSpan.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan.getSpanContext().getTraceId())));

    Assert.assertEquals(1, counter.get());
  }

  @Test
  public void invalidUpstreamParent() {
    Message sqsMessage =
        Message.builder()
            .body("Hello")
            .attributesWithStrings(
                Collections.singletonMap(
                    "AWSTraceHeader", "Root=1-55555555-invalid;Parent=1234567890123456;Sampled=1"))
            .build();

    AtomicInteger counter = new AtomicInteger(0);

    SqsMessageHandler messageHandler =
        new SqsMessageHandler(getOpenTelemetry(), "destination", MessageOperation.RECEIVE) {
          @Override
          protected void doHandleMessages(Collection<Message> messages) {
            counter.getAndIncrement();
          }
        };

    Span parentSpan = getOpenTelemetry().getTracer("test").spanBuilder("test").startSpan();

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
            .body("Hello")
            .attributesWithStrings(
                Collections.singletonMap(
                    "AWSTraceHeader",
                    "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=1"))
            .build();

    AtomicInteger counter = new AtomicInteger(0);

    SqsMessageHandler messageHandler =
        new SqsMessageHandler(getOpenTelemetry(), "destination", MessageOperation.RECEIVE) {
          @Override
          protected void doHandleMessages(Collection<Message> messages) {
            counter.getAndIncrement();
            throw new RuntimeException("Injected Error");
          }
        };

    Span parentSpan = getOpenTelemetry().getTracer("test").spanBuilder("test").startSpan();

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
                    span.hasName("destination receive")
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
                        .hasAttribute(SemanticAttributes.MESSAGING_OPERATION, "receive")
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, "destination")
                        .hasAttribute(SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, 5L)
                        .hasTotalAttributeCount(4)
                        .hasParentSpanId(parentSpan.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan.getSpanContext().getTraceId())));

    Assert.assertEquals(1, counter.get());
  }
}
