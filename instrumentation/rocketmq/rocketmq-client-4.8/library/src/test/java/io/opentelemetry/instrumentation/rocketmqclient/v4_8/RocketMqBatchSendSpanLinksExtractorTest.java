/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static java.util.Arrays.asList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class RocketMqBatchSendSpanLinksExtractorTest {

  @Test
  void delegatesCreationContextsWithoutFiltering() {
    SpanContext invalidWithTraceState =
        SpanContext.create(
            TraceId.getInvalid(),
            SpanId.getInvalid(),
            TraceFlags.getDefault(),
            TraceState.builder().put("vendor", "value").build());
    SpanContext valid =
        SpanContext.create(
            "00000000000000000000000000000001",
            "0000000000000001",
            TraceFlags.getDefault(),
            TraceState.getDefault());
    SendMessageContext request = new SendMessageContext();
    RocketMqBatchSendSpanLinksExtractor.setContexts(
        request,
        asList(
            Context.root(),
            Context.root().with(Span.wrap(invalidWithTraceState)),
            Context.root().with(Span.wrap(valid))));
    SpanLinksBuilder spanLinks = mock(SpanLinksBuilder.class);
    try {
      new RocketMqBatchSendSpanLinksExtractor().extract(spanLinks, Context.root(), request);

      InOrder order = inOrder(spanLinks);
      order.verify(spanLinks).addLink(SpanContext.getInvalid());
      order.verify(spanLinks).addLink(invalidWithTraceState);
      order.verify(spanLinks).addLink(valid);
      order.verifyNoMoreInteractions();
    } finally {
      RocketMqBatchSendSpanLinksExtractor.clearContexts(request);
    }
  }
}
