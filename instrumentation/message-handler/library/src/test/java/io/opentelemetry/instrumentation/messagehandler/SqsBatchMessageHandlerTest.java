/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.messagehandler;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class SqsBatchMessageHandlerTest {

  @RegisterExtension
  public static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  public void simple() {
    SQSEvent.SQSMessage sqsMessage = newMessage();

    sqsMessage.setAttributes(
        Collections.singletonMap(
            "AWSTraceHeader",
            "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=1"));

    AtomicInteger counter = new AtomicInteger(0);

    SqsBatchMessageHandler messageHandler =
        new SqsBatchMessageHandler(testing.getOpenTelemetrySdk()) {
          @Override
          protected void doHandleMessages(Collection<SQSEvent.SQSMessage> messages) {
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
                    span.hasName("Batch Message")
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
  public void simpleOtelUpstream() {
    SQSEvent.SQSMessage sqsMessage = newMessage();

    sqsMessage.setAttributes(
        Collections.singletonMap(
            "AWSTraceHeader", "00-ff000000000000000000000000000041-ff00000000000041-01"));

    AtomicInteger counter = new AtomicInteger(0);

    SqsBatchMessageHandler messageHandler =
        new SqsBatchMessageHandler(testing.getOpenTelemetrySdk()) {
          @Override
          protected void doHandleMessages(Collection<SQSEvent.SQSMessage> messages) {
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
                    span.hasName("Batch Message")
                        .hasLinks(
                            LinkData.create(
                                SpanContext.createFromRemoteParent(
                                    "ff000000000000000000000000000041",
                                    "ff00000000000041",
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
    List<SQSEvent.SQSMessage> sqsMessages = new LinkedList<>();

    SQSEvent.SQSMessage sqsMessage1 = newMessage();
    sqsMessage1.setAttributes(
        Collections.singletonMap(
            "AWSTraceHeader",
            "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=1"));
    sqsMessages.add(sqsMessage1);

    SQSEvent.SQSMessage sqsMessage2 = newMessage();
    sqsMessage2.setAttributes(
        Collections.singletonMap(
            "AWSTraceHeader",
            "Root=1-44444444-123456789012345678901234;Parent=2481624816248161;Sampled=0"));
    sqsMessages.add(sqsMessage2);

    AtomicInteger counter = new AtomicInteger(0);

    SqsBatchMessageHandler messageHandler =
        new SqsBatchMessageHandler(testing.getOpenTelemetrySdk()) {
          @Override
          protected void doHandleMessages(Collection<SQSEvent.SQSMessage> messages) {
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
                    span.hasName("Batch Message")
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
    SQSEvent.SQSMessage sqsMessage1 = newMessage();
    sqsMessage1.setAttributes(
        Collections.singletonMap(
            "AWSTraceHeader",
            "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=1"));

    SQSEvent.SQSMessage sqsMessage2 = newMessage();
    sqsMessage2.setAttributes(
        Collections.singletonMap(
            "AWSTraceHeader",
            "Root=1-44444444-123456789012345678901234;Parent=2481624816248161;Sampled=0"));

    AtomicInteger counter = new AtomicInteger(0);

    SqsBatchMessageHandler messageHandler =
        new SqsBatchMessageHandler(testing.getOpenTelemetrySdk(), MessageOperation.PROCESS.name()) {
          @Override
          protected void doHandleMessages(Collection<SQSEvent.SQSMessage> messages) {
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
                    span.hasName("Batch Message")
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
                    span.hasName("Batch Message")
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
        new SqsBatchMessageHandler(testing.getOpenTelemetrySdk()) {
          @Override
          protected void doHandleMessages(Collection<SQSEvent.SQSMessage> messages) {
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
                    span.hasName("Batch Message")
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
    SQSEvent.SQSMessage sqsMessage = newMessage();

    sqsMessage.setAttributes(
        Collections.singletonMap(
            "AWSTraceHeader",
            "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=1"));

    AtomicInteger counter = new AtomicInteger(0);

    SqsBatchMessageHandler messageHandler =
        new SqsBatchMessageHandler(
            testing.getOpenTelemetrySdk(), MessageOperation.PROCESS.name(), "New Name") {
          @Override
          protected void doHandleMessages(Collection<SQSEvent.SQSMessage> messages) {
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
    SQSEvent.SQSMessage sqsMessage = newMessage();

    sqsMessage.setAttributes(
        Collections.singletonMap(
            "AWSTraceHeader", "Root=1-55555555-invalid;Parent=1234567890123456;Sampled=1"));

    AtomicInteger counter = new AtomicInteger(0);

    SqsBatchMessageHandler messageHandler =
        new SqsBatchMessageHandler(testing.getOpenTelemetrySdk()) {
          @Override
          protected void doHandleMessages(Collection<SQSEvent.SQSMessage> messages) {
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
                    span.hasName("Batch Message")
                        .hasTotalRecordedLinks(0)
                        .hasAttribute(
                            SemanticAttributes.MESSAGING_OPERATION, MessageOperation.RECEIVE.name())
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasTotalAttributeCount(2)
                        .hasParentSpanId(parentSpan.getSpanContext().getSpanId())
                        .hasTraceId(parentSpan.getSpanContext().getTraceId())));

    Assert.assertEquals(1, counter.get());
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
