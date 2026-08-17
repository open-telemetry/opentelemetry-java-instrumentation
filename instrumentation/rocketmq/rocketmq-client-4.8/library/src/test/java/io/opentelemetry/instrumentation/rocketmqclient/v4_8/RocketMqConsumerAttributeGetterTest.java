/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.ConsumeReturnType;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;

class RocketMqConsumerAttributeGetterTest {

  private final RocketMqConsumerAttributeGetter getter = new RocketMqConsumerAttributeGetter();

  private static RocketMqConsumerRequest request() {
    return new RocketMqConsumerRequest(mock(MessageExt.class), "consumer-group", 1, null);
  }

  @Test
  void doesNotReportErrorTypeOnSuccess() {
    ConsumeMessageContext response = mock(ConsumeMessageContext.class);
    when(response.isSuccess()).thenReturn(true);

    assertThat(getter.getErrorType(request(), response, null)).isNull();
  }

  @Test
  void reportsConsumeReturnTypeAsErrorType() {
    // rocketmq reports RECONSUME_LATER as the status even when the listener threw
    ConsumeMessageContext response = mock(ConsumeMessageContext.class);
    when(response.isSuccess()).thenReturn(false);
    when(response.getProps())
        .thenReturn(singletonMap(MixAll.CONSUME_CONTEXT_TYPE, ConsumeReturnType.EXCEPTION.name()));

    assertThat(getter.getErrorType(request(), response, null))
        .isEqualTo(ConsumeReturnType.EXCEPTION.name());
  }

  @Test
  void reportsTimedOutConsumeReturnTypeAsErrorTypeWhenConsumeStatusIsSuccess() {
    // rocketmq derives the consume return type from the duration of the consume operation as well
    // as from its outcome, so a listener that exceeds the configured consume timeout is reported as
    // TIME_OUT even when it eventually returns a success status
    ConsumeMessageContext response = mock(ConsumeMessageContext.class);
    when(response.getProps())
        .thenReturn(singletonMap(MixAll.CONSUME_CONTEXT_TYPE, ConsumeReturnType.TIME_OUT.name()));

    assertThat(getter.getErrorType(request(), response, null))
        .isEqualTo(ConsumeReturnType.TIME_OUT.name());
  }

  @Test
  void doesNotReportErrorTypeOnSuccessfulConsumeReturnType() {
    ConsumeMessageContext response = mock(ConsumeMessageContext.class);
    when(response.getProps())
        .thenReturn(singletonMap(MixAll.CONSUME_CONTEXT_TYPE, ConsumeReturnType.SUCCESS.name()));

    assertThat(getter.getErrorType(request(), response, null)).isNull();
  }

  @Test
  void fallsBackToConsumeStatusWhenConsumeReturnTypeIsMissing() {
    ConsumeMessageContext response = mock(ConsumeMessageContext.class);
    when(response.isSuccess()).thenReturn(false);
    when(response.getStatus()).thenReturn(ConsumeConcurrentlyStatus.RECONSUME_LATER.name());

    assertThat(getter.getErrorType(request(), response, null))
        .isEqualTo(ConsumeConcurrentlyStatus.RECONSUME_LATER.name());
  }

  @Test
  void reportsMessageHeaderOfSingleMessage() {
    MessageExt message = mock(MessageExt.class);
    when(message.getProperties()).thenReturn(singletonMap("test-header", "test-value"));

    RocketMqConsumerRequest request =
        new RocketMqConsumerRequest(message, "consumer-group", 1, null);

    assertThat(getter.getMessageHeader(request, "test-header")).containsExactly("test-value");
  }

  @Test
  void doesNotReportMessageHeadersOfBatch() {
    MessageExt first = mock(MessageExt.class);
    MessageExt second = mock(MessageExt.class);

    RocketMqConsumerRequest request =
        new RocketMqConsumerRequest(asList(first, second), "consumer-group", 2, null);

    // merging the headers of every message into one attribute would lose which message each value
    // came from, so they belong on the span links instead
    assertThat(getter.getMessageHeader(request, "test-header")).isEmpty();
  }

  @Test
  void lazilyComputesOnlyRequestedBatchAttribute() {
    MessageExt first = mock(MessageExt.class);
    MessageExt second = mock(MessageExt.class);
    when(first.getTopic()).thenReturn("topic");
    when(second.getTopic()).thenReturn("topic");
    clearInvocations(first, second);

    RocketMqConsumerRequest request =
        new RocketMqConsumerRequest(asList(first, second), "consumer-group", 2, null);

    verifyNoInteractions(first, second);
    assertThat(request.getDestination()).isEqualTo("topic");
    assertThat(request.getDestination()).isEqualTo("topic");
    verify(first).getTopic();
    verify(second).getTopic();
    verifyNoMoreInteractions(first, second);
  }
}
