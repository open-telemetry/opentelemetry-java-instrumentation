/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import org.junit.jupiter.api.Test;

class SqsEventSpanLinksExtractorTest {

  @Test
  void delegatesUntracedMessageWithAttributes() {
    SQSMessage message = new SQSMessage();
    message.setMessageId("message-1");
    SQSEvent event = new SQSEvent();
    event.setRecords(singletonList(message));
    SpanLinksBuilder spanLinks = mock(SpanLinksBuilder.class);

    new SqsEventSpanLinksExtractor().extract(spanLinks, Context.root(), event);

    verify(spanLinks)
        .addLink(SpanContext.getInvalid(), Attributes.of(MESSAGING_MESSAGE_ID, "message-1"));
  }

  @Test
  void delegatesUntracedMessagesWithoutAttributes() {
    SQSMessage message1 = new SQSMessage();
    message1.setEventSourceArn("arn:aws:sqs:us-east-2:123456789012:queue1");
    SQSMessage message2 = new SQSMessage();
    message2.setEventSourceArn("arn:aws:sqs:us-east-2:123456789012:queue1");
    SQSEvent event = new SQSEvent();
    event.setRecords(asList(message1, message2));
    SpanLinksBuilder spanLinks = mock(SpanLinksBuilder.class);

    new SqsEventSpanLinksExtractor().extract(spanLinks, Context.root(), event);

    verify(spanLinks, times(2)).addLink(SpanContext.getInvalid(), Attributes.empty());
  }

  @Test
  void delegatesTracedMessageWithoutAttributes() {
    SQSMessage message = new SQSMessage();
    message.setEventSourceArn("arn:aws:sqs:us-east-2:123456789012:queue1");
    message.setAttributes(
        singletonMap(
            "AWSTraceHeader",
            "Root=1-5759e988-bd862e3fe1be46a994272793;Parent=53995c3f42cd8ad8;Sampled=1"));
    SQSEvent event = new SQSEvent();
    event.setRecords(singletonList(message));
    SpanLinksBuilder spanLinks = mock(SpanLinksBuilder.class);

    new SqsEventSpanLinksExtractor().extract(spanLinks, Context.root(), event);

    verify(spanLinks)
        .addLink(
            SpanContext.createFromRemoteParent(
                "5759e988bd862e3fe1be46a994272793",
                "53995c3f42cd8ad8",
                TraceFlags.getSampled(),
                TraceState.getDefault()),
            Attributes.empty());
  }

  @Test
  void delegatesUntracedMessagesWithOnlyDestinationAttributes() {
    SQSMessage message1 = new SQSMessage();
    message1.setEventSourceArn("arn:aws:sqs:us-east-2:123456789012:queue1");
    SQSMessage message2 = new SQSMessage();
    message2.setEventSourceArn("arn:aws:sqs:us-east-2:123456789012:queue2");
    SQSEvent event = new SQSEvent();
    event.setRecords(asList(message1, message2));
    SpanLinksBuilder spanLinks = mock(SpanLinksBuilder.class);

    new SqsEventSpanLinksExtractor().extract(spanLinks, Context.root(), event);

    verify(spanLinks)
        .addLink(SpanContext.getInvalid(), Attributes.of(MESSAGING_DESTINATION_NAME, "queue1"));
    verify(spanLinks)
        .addLink(SpanContext.getInvalid(), Attributes.of(MESSAGING_DESTINATION_NAME, "queue2"));
  }
}
