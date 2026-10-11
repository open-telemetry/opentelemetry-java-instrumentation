/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.List;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.junit.jupiter.api.Test;

class RocketMqBatchSendSpanLinksExtractorTest {

  @Test
  void storesContextsDirectlyInAnIsolatedNamedField() {
    SendMessageContext request = new SendMessageContext();
    Context unrelatedContext = Context.root();
    VirtualField<SendMessageContext, List<Context>> unnamed =
        VirtualField.find(SendMessageContext.class, List.class);
    unnamed.set(request, singletonList(unrelatedContext));
    List<Context> contexts = singletonList(creationContext());

    RocketMqBatchSendSpanLinksExtractor.setContexts(request, contexts);

    VirtualField<SendMessageContext, List<Context>> named =
        VirtualField.find(SendMessageContext.class, List.class, "messageCreationContexts");
    assertThat(named.get(request)).isSameAs(contexts);
    assertThat(unnamed.get(request)).containsExactly(unrelatedContext);
    assertThat(RocketMqBatchSendSpanLinksExtractor.isBatchRequest(request)).isTrue();
    assertThat(RocketMqBatchSendSpanLinksExtractor.getBatchMessageCount(request)).isEqualTo(1);
    assertThat(RocketMqBatchSendSpanLinksExtractor.allMessagesHaveCreationContext(request))
        .isTrue();

    SpanLinksBuilder links = mock(SpanLinksBuilder.class);
    new RocketMqBatchSendSpanLinksExtractor().extract(links, Context.root(), request);
    verify(links).addLink(Span.fromContext(contexts.get(0)).getSpanContext());

    RocketMqBatchSendSpanLinksExtractor.clearContexts(request);

    assertThat(RocketMqBatchSendSpanLinksExtractor.isBatchRequest(request)).isFalse();
    assertThat(RocketMqBatchSendSpanLinksExtractor.getBatchMessageCount(request)).isZero();
    assertThat(RocketMqBatchSendSpanLinksExtractor.allMessagesHaveCreationContext(request))
        .isFalse();
    assertThat(unnamed.get(request)).containsExactly(unrelatedContext);
    SpanLinksBuilder clearedLinks = mock(SpanLinksBuilder.class);
    new RocketMqBatchSendSpanLinksExtractor().extract(clearedLinks, Context.root(), request);
    verifyNoInteractions(clearedLinks);
  }

  @Test
  void preservesEmptyAndMissingCreationContexts() {
    SendMessageContext request = new SendMessageContext();
    RocketMqBatchSendSpanLinksExtractor.setContexts(request, emptyList());

    assertThat(RocketMqBatchSendSpanLinksExtractor.isBatchRequest(request)).isTrue();
    assertThat(RocketMqBatchSendSpanLinksExtractor.getBatchMessageCount(request)).isZero();
    assertThat(RocketMqBatchSendSpanLinksExtractor.allMessagesHaveCreationContext(request))
        .isFalse();

    RocketMqBatchSendSpanLinksExtractor.setContexts(
        request, asList(creationContext(), Context.root()));

    assertThat(RocketMqBatchSendSpanLinksExtractor.getBatchMessageCount(request)).isEqualTo(2);
    assertThat(RocketMqBatchSendSpanLinksExtractor.allMessagesHaveCreationContext(request))
        .isFalse();
  }

  private static Context creationContext() {
    return Context.root()
        .with(
            Span.wrap(
                SpanContext.create(
                    "00000000000000000000000000000001",
                    "0000000000000001",
                    TraceFlags.getSampled(),
                    TraceState.getDefault())));
  }
}
