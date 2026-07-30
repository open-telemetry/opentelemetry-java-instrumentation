/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RocketMqConsumerInstrumenterTest {

  @Mock
  private Instrumenter<RocketMqConsumerRequest, ConsumeMessageContext> singleProcessInstrumenter;

  @Mock
  private Instrumenter<RocketMqConsumerRequest, ConsumeMessageContext> batchProcessInstrumenter;

  @Mock private Instrumenter<RocketMqConsumerRequest, Void> batchReceiveInstrumenter;

  @Test
  void usesEmptyNamespaceByDefault() {
    RocketMqConsumerRequest request =
        new RocketMqConsumerRequest(mock(MessageExt.class), "consumer-group", 1, null);

    assertThat(request.getNamespace()).isEmpty();
  }

  @Test
  void endsBatchReceiveWhenAllProcessSpansAreSuppressed() {
    RocketMqConsumerInstrumenter instrumenter =
        new RocketMqConsumerInstrumenter(
            singleProcessInstrumenter, batchProcessInstrumenter, batchReceiveInstrumenter);
    Context parentContext = Context.root();
    Context receiveContext = mock(Context.class);
    when(batchReceiveInstrumenter.shouldStart(same(parentContext), any())).thenReturn(true);
    when(batchReceiveInstrumenter.start(same(parentContext), any())).thenReturn(receiveContext);

    RocketMqConsumerInstrumenter.ConsumerContext consumerContext =
        requireNonNull(
            instrumenter.start(
                parentContext,
                asList(mock(MessageExt.class), mock(MessageExt.class)),
                "consumer-group",
                "namespace"));
    ConsumeMessageContext response = mock(ConsumeMessageContext.class);
    instrumenter.end(consumerContext, response);

    verify(batchProcessInstrumenter, times(2)).shouldStart(any(), any());
    verify(batchReceiveInstrumenter)
        .end(same(receiveContext), same(consumerContext.getRequest()), same(null), same(null));
    verifyNoInteractions(singleProcessInstrumenter);
  }

  @Test
  void startsBatchProcessSpansWhenReceiveSpanIsSuppressed() {
    RocketMqConsumerInstrumenter instrumenter =
        new RocketMqConsumerInstrumenter(
            singleProcessInstrumenter, batchProcessInstrumenter, batchReceiveInstrumenter);
    Context parentContext = Context.root();
    Context processContext = mock(Context.class);
    when(batchReceiveInstrumenter.shouldStart(same(parentContext), any())).thenReturn(false);
    when(batchProcessInstrumenter.shouldStart(same(parentContext), any())).thenReturn(true);
    when(batchProcessInstrumenter.start(same(parentContext), any())).thenReturn(processContext);

    RocketMqConsumerInstrumenter.ConsumerContext consumerContext =
        requireNonNull(
            instrumenter.start(
                parentContext,
                asList(mock(MessageExt.class), mock(MessageExt.class)),
                "consumer-group",
                "namespace"));
    ConsumeMessageContext response = mock(ConsumeMessageContext.class);
    instrumenter.end(consumerContext, response);

    assertThat(consumerContext.getContext()).isSameAs(parentContext);
    verify(batchProcessInstrumenter, times(2)).start(same(parentContext), any());
    verify(batchProcessInstrumenter, times(2))
        .end(same(processContext), any(), same(response), same(null));
    verify(batchReceiveInstrumenter, never()).start(any(), any());
    verify(batchReceiveInstrumenter, never()).end(any(), any(), any(), any());
    verifyNoInteractions(singleProcessInstrumenter);
  }
}
