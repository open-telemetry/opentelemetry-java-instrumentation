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
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class SQSBatchMessageHandlerTest {

  @RegisterExtension
  public static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  public void simple() {
    SQSEvent.SQSMessage sqsMessage = newMessage();
    SpanContext messageSpanContext = SpanContext.createFromRemoteParent(
        "55555555123456789012345678901234",
        "1234567890123456",
        TraceFlags.getSampled(),
        TraceState.getDefault());
    sqsMessage.setAttributes(Collections.singletonMap("AWSTraceHeader", "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=1"));

    AtomicInteger counter = new AtomicInteger(0);

    SQSBatchMessageHandler messageHandler = new SQSBatchMessageHandler(testing.getOpenTelemetrySdk()) {
      @Override
      protected void doHandleMessages(Collection<SQSEvent.SQSMessage> messages) {
        counter.getAndIncrement();
      }
    };

    Span parentSpan = testing.getOpenTelemetrySdk().getTracer("test").spanBuilder("test").startSpan();

    messageHandler.handleMessages(Collections.singletonList(sqsMessage));

    parentSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test")),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("Batch Message Handler")
                        .hasLinks(LinkData.create(messageSpanContext))
                        .hasTotalRecordedLinks(1)
                        .hasAttribute(SemanticAttributes.MESSAGING_OPERATION, MessageOperation.RECEIVE.name())
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasTotalAttributeCount(2)
            ));

    Assert.assertEquals(1, counter.get());
  }

  @Test
  public void multipleMessages() {
    List<SQSEvent.SQSMessage> sqsMessages = new LinkedList<>();

    SQSEvent.SQSMessage sqsMessage1 = newMessage();
    sqsMessage1.setAttributes(Collections.singletonMap("AWSTraceHeader", "Root=1-55555555-123456789012345678901234;Parent=1234567890123456;Sampled=1"));
    sqsMessages.add(sqsMessage1);

    SQSEvent.SQSMessage sqsMessage2 = newMessage();
    sqsMessage2.setAttributes(Collections.singletonMap("AWSTraceHeader", "Root=1-44444444-123456789012345678901234;Parent=2481624816248161;Sampled=0"));
    sqsMessages.add(sqsMessage2);

    AtomicInteger counter = new AtomicInteger(0);

    SQSBatchMessageHandler messageHandler = new SQSBatchMessageHandler(testing.getOpenTelemetrySdk()) {
      @Override
      protected void doHandleMessages(Collection<SQSEvent.SQSMessage> messages) {
        counter.getAndIncrement();
      }
    };

    Span parentSpan = testing.getOpenTelemetrySdk().getTracer("test").spanBuilder("test").startSpan();

    messageHandler.handleMessages(sqsMessages);

    parentSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test")),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("Batch Message Handler")
                        .hasLinks(
                            LinkData.create(SpanContext.createFromRemoteParent(
                              "55555555123456789012345678901234",
                              "1234567890123456",
                              TraceFlags.getSampled(),
                              TraceState.getDefault())),
                            LinkData.create(SpanContext.createFromRemoteParent(
                                "44444444123456789012345678901234",
                                "2481624816248161",
                                TraceFlags.getDefault(),
                                TraceState.getDefault())))
                        .hasTotalRecordedLinks(2)
                        .hasAttribute(SemanticAttributes.MESSAGING_OPERATION, MessageOperation.RECEIVE.name())
                        .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")
                        .hasTotalAttributeCount(2)
            ));

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
