/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static java.util.Collections.singletonList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
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
}
