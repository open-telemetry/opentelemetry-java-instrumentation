/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.Message;

public class SqsMessageHandlerTest extends XrayTestInstrumenter {
  private static class SqsMessageHandlerImpl extends SqsMessageHandler<Message> {
    public final AtomicInteger handleCalls = new AtomicInteger();

    public SqsMessageHandlerImpl(OpenTelemetry openTelemetry,
        String destination, SpanKindExtractor<Collection<Message>> spanKindExtractor) {
      super(openTelemetry, destination, spanKindExtractor);
    }

    @Override
    protected void doHandle(Collection<Message> request) {
      handleCalls.getAndIncrement();
    }
  }

  private static class LambdaSqsMessageHandlerImpl extends SqsMessageHandler<SQSEvent.SQSMessage> {
    public final AtomicInteger handleCalls = new AtomicInteger();

    public LambdaSqsMessageHandlerImpl(OpenTelemetry openTelemetry,
        String destination, SpanKindExtractor<Collection<SQSEvent.SQSMessage>> spanKindExtractor) {
      super(openTelemetry, destination, spanKindExtractor);
    }

    @Override
    protected void doHandle(Collection<SQSEvent.SQSMessage> request) {
      handleCalls.getAndIncrement();
    }
  }

  @Test
  public void simple() {
    SqsMessageHandlerImpl messageHandler = new SqsMessageHandlerImpl(
        getOpenTelemetry(),
        "destination",
        SpanKindExtractor.alwaysServer());

    List<Message> messages = new LinkedList<>();
    messages.add(Message.builder()
        .body("Hello")
        .attributesWithStrings(
            Collections.singletonMap(
                "AWSTraceHeader",
                "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=1"))
        .build());

    Span parentSpan = getOpenTelemetry().getTracer("test").spanBuilder("test").startSpan();

    try (Scope scope = parentSpan.makeCurrent()) {
      messageHandler.handle(messages);
    }

    parentSpan.end();

    assertThat(messageHandler.handleCalls.get()).isEqualTo(1);

    waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("destination process")
                        .hasKind(SpanKind.SERVER)
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
                        .hasAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, "destination")
                        .hasAttribute(SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, 5L)
                        .hasTotalAttributeCount(4)
                        .hasParentSpanId(parentSpan.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan.getSpanContext().getTraceId())));
  }

  @Test
  public void lambdaSimple() {
    LambdaSqsMessageHandlerImpl messageHandler = new LambdaSqsMessageHandlerImpl(
        getOpenTelemetry(),
        "destination",
        SpanKindExtractor.alwaysServer());

    List<SQSEvent.SQSMessage> messages = new LinkedList<>();
    SQSEvent.SQSMessage message = newMessage();
    message.setBody("Hello");
    Map<String, String> attributes = new TreeMap<>();
    attributes.put("AWSTraceHeader", "Root=1-99555555-123456789012345678901234;Parent=9934567890123456;Sampled=1");
    message.setAttributes(attributes);
    messages.add(message);

    Span parentSpan = getOpenTelemetry().getTracer("test").spanBuilder("test").startSpan();

    try (Scope scope = parentSpan.makeCurrent()) {
      messageHandler.handle(messages);
    }

    parentSpan.end();

    assertThat(messageHandler.handleCalls.get()).isEqualTo(1);

    waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("destination process")
                        .hasKind(SpanKind.SERVER)
                        .hasLinks(
                            LinkData.create(
                                SpanContext.createFromRemoteParent(
                                    "99555555123456789012345678901234",
                                    "9934567890123456",
                                    TraceFlags.getSampled(),
                                    TraceState.getDefault())))
                        .hasTotalRecordedLinks(1)
                        .hasAttribute(SemanticAttributes.MESSAGING_OPERATION, "process")
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, "destination")
                        .hasAttribute(SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, 5L)
                        .hasTotalAttributeCount(4)
                        .hasParentSpanId(parentSpan.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan.getSpanContext().getTraceId())));
  }

  @Test
  public void lambdaTwoMessages() {
    List<SQSEvent.SQSMessage> messages = new LinkedList<>();
    SQSEvent.SQSMessage message = newMessage();
    message.setBody("Hello");
    Map<String, String> attributes = new TreeMap<>();
    attributes.put("AWSTraceHeader", "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=1");
    message.setAttributes(attributes);
    messages.add(message);

    SQSEvent.SQSMessage message2 = newMessage();
    message2.setBody("Hello World");
    Map<String, String> attributes2 = new TreeMap<>();
    attributes2.put("AWSTraceHeader", "Root=1-66555555-123456789012345678901234;Parent=6634567890123456;Sampled=0");
    message2.setAttributes(attributes2);
    messages.add(message2);

    LambdaSqsMessageHandlerImpl messageHandler = new LambdaSqsMessageHandlerImpl(
        getOpenTelemetry(),
        "destination",
        SpanKindExtractor.alwaysConsumer());

    Span parentSpan = getOpenTelemetry().getTracer("test").spanBuilder("test").startSpan();

    try (Scope scope = parentSpan.makeCurrent()) {
      messageHandler.handle(messages);
    }

    parentSpan.end();

    assertThat(messageHandler.handleCalls.get()).isEqualTo(1);

    waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("destination process")
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
                                    "66555555123456789012345678901234",
                                    "6634567890123456",
                                    TraceFlags.getDefault(),
                                    TraceState.getDefault())))
                        .hasTotalRecordedLinks(2)
                        .hasAttribute(SemanticAttributes.MESSAGING_OPERATION, "process")
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, "destination")
                        .hasAttribute(SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, 16L)
                        .hasTotalAttributeCount(4)
                        .hasParentSpanId(parentSpan.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan.getSpanContext().getTraceId())));
  }

  @Test
  public void twoMessages() {
    List<Message> messages = new LinkedList<Message>();
    messages.add(Message.builder()
        .body("Hello")
        .attributesWithStrings(
            Collections.singletonMap(
                "AWSTraceHeader",
                "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=1"))
        .build());
    messages.add(Message.builder()
        .body("Hello World")
        .attributesWithStrings(
            Collections.singletonMap(
                "AWSTraceHeader",
                "Root=1-66555555-123456789012345678901234;Parent=6634567890123456;Sampled=0"))
        .build());

    SqsMessageHandlerImpl messageHandler = new SqsMessageHandlerImpl(
        getOpenTelemetry(),
        "destination",
        SpanKindExtractor.alwaysConsumer());

    Span parentSpan = getOpenTelemetry().getTracer("test").spanBuilder("test").startSpan();

    try (Scope scope = parentSpan.makeCurrent()) {
      messageHandler.handle(messages);
    }

    parentSpan.end();

    assertThat(messageHandler.handleCalls.get()).isEqualTo(1);

    waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("destination process")
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
                                    "66555555123456789012345678901234",
                                    "6634567890123456",
                                    TraceFlags.getDefault(),
                                    TraceState.getDefault())))
                        .hasTotalRecordedLinks(2)
                        .hasAttribute(SemanticAttributes.MESSAGING_OPERATION, "process")
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, "destination")
                        .hasAttribute(SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, 16L)
                        .hasTotalAttributeCount(4)
                        .hasParentSpanId(parentSpan.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan.getSpanContext().getTraceId())));
  }

  @Test
  public void twoRuns() {
    List<Message> messages1 = new LinkedList<Message>();
    messages1.add(Message.builder()
      .body("Hello")
      .attributesWithStrings(
          Collections.singletonMap(
              "AWSTraceHeader",
              "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=0"))
      .build());

    List<Message> messages2 = new LinkedList<Message>();
    messages2.add(Message.builder()
      .body("SecondMessage")
      .attributesWithStrings(
          Collections.singletonMap(
              "AWSTraceHeader",
              "Root=1-77555555-123456789012345678901234;Parent=7734567890123456;Sampled=1"))
      .build());

    SqsMessageHandlerImpl messageHandler = new SqsMessageHandlerImpl(
        getOpenTelemetry(),
        "destination",
        SpanKindExtractor.alwaysServer());

    Span parentSpan1 = getOpenTelemetry().getTracer("test").spanBuilder("test1").startSpan();
    try (Scope scope = parentSpan1.makeCurrent()) {
      messageHandler.handle(messages1);
    }
    parentSpan1.end();

    Span parentSpan2 = getOpenTelemetry().getTracer("test").spanBuilder("test2").startSpan();
    try (Scope scope = parentSpan2.makeCurrent()) {
      messageHandler.handle(messages2);
    }
    parentSpan2.end();

    assertThat(messageHandler.handleCalls.get()).isEqualTo(2);

    waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test1").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("destination process")
                        .hasKind(SpanKind.SERVER)
                        .hasLinks(
                            LinkData.create(
                                SpanContext.createFromRemoteParent(
                                    "55555555123456789012345678901234",
                                    "1234567890123456",
                                    TraceFlags.getDefault(),
                                    TraceState.getDefault())))
                        .hasTotalRecordedLinks(1)
                        .hasAttribute(SemanticAttributes.MESSAGING_OPERATION, "process")
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, "destination")
                        .hasAttribute(SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, 5L)
                        .hasTotalAttributeCount(4)
                        .hasParentSpanId(parentSpan1.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan1.getSpanContext().getTraceId())),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test2").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("destination process")
                        .hasKind(SpanKind.SERVER)
                        .hasLinks(
                            LinkData.create(
                                SpanContext.createFromRemoteParent(
                                    "77555555123456789012345678901234",
                                    "7734567890123456",
                                    TraceFlags.getSampled(),
                                    TraceState.getDefault())))
                        .hasTotalRecordedLinks(1)
                        .hasAttribute(SemanticAttributes.MESSAGING_OPERATION, "process")
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, "destination")
                        .hasAttribute(SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, 13L)
                        .hasTotalAttributeCount(4)
                        .hasParentSpanId(parentSpan2.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan2.getSpanContext().getTraceId())));
  }

  @Test
  public void lambdaTwoRuns() {
    List<SQSEvent.SQSMessage> messages1 = new LinkedList<>();
    List<SQSEvent.SQSMessage> messages2 = new LinkedList<>();
    SQSEvent.SQSMessage message = newMessage();
    message.setBody("Hello");
    Map<String, String> attributes = new TreeMap<>();
    attributes.put("AWSTraceHeader", "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=0");
    message.setAttributes(attributes);
    messages1.add(message);

    SQSEvent.SQSMessage message2 = newMessage();
    message2.setBody("SecondMessage");
    Map<String, String> attributes2 = new TreeMap<>();
    attributes2.put("AWSTraceHeader", "Root=1-77555555-123456789012345678901234;Parent=7734567890123456;Sampled=1");
    message2.setAttributes(attributes2);
    messages2.add(message2);

    LambdaSqsMessageHandlerImpl messageHandler = new LambdaSqsMessageHandlerImpl(
        getOpenTelemetry(),
        "destination",
        SpanKindExtractor.alwaysServer());

    Span parentSpan1 = getOpenTelemetry().getTracer("test").spanBuilder("test1").startSpan();
    try (Scope scope = parentSpan1.makeCurrent()) {
      messageHandler.handle(messages1);
    }
    parentSpan1.end();

    Span parentSpan2 = getOpenTelemetry().getTracer("test").spanBuilder("test2").startSpan();
    try (Scope scope = parentSpan2.makeCurrent()) {
      messageHandler.handle(messages2);
    }
    parentSpan2.end();

    assertThat(messageHandler.handleCalls.get()).isEqualTo(2);

    waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test1").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("destination process")
                        .hasKind(SpanKind.SERVER)
                        .hasLinks(
                            LinkData.create(
                                SpanContext.createFromRemoteParent(
                                    "55555555123456789012345678901234",
                                    "1234567890123456",
                                    TraceFlags.getDefault(),
                                    TraceState.getDefault())))
                        .hasTotalRecordedLinks(1)
                        .hasAttribute(SemanticAttributes.MESSAGING_OPERATION, "process")
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, "destination")
                        .hasAttribute(SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, 5L)
                        .hasTotalAttributeCount(4)
                        .hasParentSpanId(parentSpan1.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan1.getSpanContext().getTraceId())),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test2").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("destination process")
                        .hasKind(SpanKind.SERVER)
                        .hasLinks(
                            LinkData.create(
                                SpanContext.createFromRemoteParent(
                                    "77555555123456789012345678901234",
                                    "7734567890123456",
                                    TraceFlags.getSampled(),
                                    TraceState.getDefault())))
                        .hasTotalRecordedLinks(1)
                        .hasAttribute(SemanticAttributes.MESSAGING_OPERATION, "process")
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, "destination")
                        .hasAttribute(SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, 13L)
                        .hasTotalAttributeCount(4)
                        .hasParentSpanId(parentSpan2.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan2.getSpanContext().getTraceId())));
  }

  @Test
  public void lambdaNoMessages() {
    LambdaSqsMessageHandlerImpl messageHandler = new LambdaSqsMessageHandlerImpl(
        getOpenTelemetry(),
        "destination",
        SpanKindExtractor.alwaysServer());

    Span parentSpan = getOpenTelemetry().getTracer("test").spanBuilder("test").startSpan();
    try (Scope scope = parentSpan.makeCurrent()) {
      messageHandler.handle(new LinkedList<>());
    }
    parentSpan.end();

    assertThat(messageHandler.handleCalls.get()).isEqualTo(1);

    waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("destination process")
                        .hasKind(SpanKind.SERVER)
                        .hasTotalRecordedLinks(0)
                        .hasAttribute(SemanticAttributes.MESSAGING_OPERATION, "process")
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, "destination")
                        .hasAttribute(SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, 0L)
                        .hasTotalAttributeCount(4)
                        .hasParentSpanId(parentSpan.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan.getSpanContext().getTraceId())));
  }

  @Test
  public void noMessages() {
    SqsMessageHandlerImpl messageHandler = new SqsMessageHandlerImpl(
        getOpenTelemetry(),
        "destination",
        SpanKindExtractor.alwaysServer());

    Span parentSpan = getOpenTelemetry().getTracer("test").spanBuilder("test").startSpan();
    try (Scope scope = parentSpan.makeCurrent()) {
      messageHandler.handle(new LinkedList<>());
    }
    parentSpan.end();

    assertThat(messageHandler.handleCalls.get()).isEqualTo(1);

    waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0),
                span ->
                    span.hasName("destination process")
                        .hasKind(SpanKind.SERVER)
                        .hasTotalRecordedLinks(0)
                        .hasAttribute(SemanticAttributes.MESSAGING_OPERATION, "process")
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, "destination")
                        .hasAttribute(SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, 0L)
                        .hasTotalAttributeCount(4)
                        .hasParentSpanId(parentSpan.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan.getSpanContext().getTraceId())));
  }

  @Test
  public void malformedTraceId() {
    List<Message> messages = new LinkedList<Message>();
    messages.add(Message.builder()
      .body("Hello")
      .attributesWithStrings(
          Collections.singletonMap(
              "AWSTraceHeader",
              "Root=1-55555555-error;Parent=1234567890123456;Sampled=1"))
      .build());

    SqsMessageHandlerImpl messageHandler = new SqsMessageHandlerImpl(
        getOpenTelemetry(),
        "destination",
        SpanKindExtractor.alwaysServer());

    Span parentSpan = getOpenTelemetry().getTracer("test").spanBuilder("test").startSpan();

    assertThrows(
        RuntimeException.class,
        () -> {
          try (Scope scope = parentSpan.makeCurrent()) {
            messageHandler.handle(messages);
          }
        });

    parentSpan.end();

    assertThat(messageHandler.handleCalls.get()).isEqualTo(0);

    waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0)));
  }

  @Test
  public void lambdaMalformedTraceId() {
    List<SQSEvent.SQSMessage> messages = new LinkedList<>();
    SQSEvent.SQSMessage message = newMessage();
    message.setBody("Hello");
    Map<String, String> attributes = new TreeMap<>();
    attributes.put("AWSTraceHeader", "Root=1-55555555-error;Parent=1234567890123456;Sampled=1");
    message.setAttributes(attributes);
    messages.add(message);

    LambdaSqsMessageHandlerImpl messageHandler = new LambdaSqsMessageHandlerImpl(
        getOpenTelemetry(),
        "destination",
        SpanKindExtractor.alwaysServer());

    Span parentSpan = getOpenTelemetry().getTracer("test").spanBuilder("test").startSpan();

    assertThrows(
        RuntimeException.class,
        () -> {
          try (Scope scope = parentSpan.makeCurrent()) {
            messageHandler.handle(messages);
          }
        });

    parentSpan.end();

    assertThat(messageHandler.handleCalls.get()).isEqualTo(0);

    waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasTotalRecordedLinks(0)));
  }

  private static SQSEvent.SQSMessage newMessage() {
    try {
      Constructor<SQSEvent.SQSMessage> ctor = SQSEvent.SQSMessage.class.getDeclaredConstructor();
      ctor.setAccessible(true);
      return ctor.newInstance();
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }
}
