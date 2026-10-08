/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;

class SqsEventSpanLinksExtractorTest {

  @ParameterizedTest
  @MethodSource("traceHeaders")
  void delegatesMessageLink(String traceHeader, SpanContext expected) {
    SQSMessage message = new SQSMessage();
    message.setMessageId("message-1");
    if (traceHeader != null) {
      message.setAttributes(singletonMap("AWSTraceHeader", traceHeader));
    }
    SQSEvent event = new SQSEvent();
    event.setRecords(singletonList(message));
    SpanLinksBuilder spanLinks = mock(SpanLinksBuilder.class);
    Context parentContext =
        Context.root()
            .with(
                Span.wrap(
                    SpanContext.create(
                        "00000000000000000000000000000003",
                        "0000000000000004",
                        TraceFlags.getSampled(),
                        TraceState.getDefault())));

    new SqsEventSpanLinksExtractor().extract(spanLinks, parentContext, event);

    verify(spanLinks).addLink(expected, Attributes.of(MESSAGING_MESSAGE_ID, "message-1"));
  }

  private static Stream<Arguments> traceHeaders() {
    return Stream.of(
        argumentSet("missing header", null, SpanContext.getInvalid()),
        argumentSet("malformed header", "invalid", SpanContext.getInvalid()),
        argumentSet(
            "valid header",
            "Root=1-00000001-000000000000000000000002;Parent=0000000000000002;Sampled=1",
            SpanContext.createFromRemoteParent(
                "00000001000000000000000000000002",
                "0000000000000002",
                TraceFlags.getSampled(),
                TraceState.getDefault())));
  }

  @Test
  void delegatesSeparateUntracedMessagesWithTheirAttributes() {
    SQSMessage first = new SQSMessage();
    first.setMessageId("message-1");
    first.setEventSourceArn("arn:aws:sqs:us-east-2:123456789012:queue-1");
    SQSMessage second = new SQSMessage();
    second.setMessageId("message-2");
    second.setEventSourceArn("arn:aws:sqs:us-east-2:123456789012:queue-2");
    SQSEvent event = new SQSEvent();
    event.setRecords(asList(first, second));
    SpanLinksBuilder spanLinks = mock(SpanLinksBuilder.class);

    new SqsEventSpanLinksExtractor().extract(spanLinks, Context.root(), event);

    InOrder ordered = inOrder(spanLinks);
    ordered
        .verify(spanLinks)
        .addLink(
            SpanContext.getInvalid(),
            Attributes.of(
                MESSAGING_MESSAGE_ID, "message-1", MESSAGING_DESTINATION_NAME, "queue-1"));
    ordered
        .verify(spanLinks)
        .addLink(
            SpanContext.getInvalid(),
            Attributes.of(
                MESSAGING_MESSAGE_ID, "message-2", MESSAGING_DESTINATION_NAME, "queue-2"));
    ordered.verifyNoMoreInteractions();
  }

  @Test
  void delegatesEmptyInvalidLinkToBuilder() {
    SQSEvent event = new SQSEvent();
    event.setRecords(singletonList(new SQSMessage()));
    SpanLinksBuilder spanLinks = mock(SpanLinksBuilder.class);

    new SqsEventSpanLinksExtractor().extract(spanLinks, Context.root(), event);

    verify(spanLinks).addLink(SpanContext.getInvalid(), Attributes.empty());
  }

  @Test
  void noRecords() {
    SQSEvent event = new SQSEvent();
    SpanLinksBuilder spanLinks = mock(SpanLinksBuilder.class);

    new SqsEventSpanLinksExtractor().extract(spanLinks, Context.root(), event);
    event.setRecords(emptyList());
    new SqsEventSpanLinksExtractor().extract(spanLinks, Context.root(), event);

    verifyNoInteractions(spanLinks);
  }
}
