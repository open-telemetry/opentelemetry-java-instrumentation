/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.RECEIVE;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.CONSUMED_MESSAGES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.jms.JmsMessageDeliveryState;
import javax.jms.Message;
import org.junit.jupiter.api.Test;

class JmsMessageInstrumentationTest {

  private static final VirtualField<Message, JmsMessageDeliveryState> JMS_DELIVERY_STATE =
      VirtualField.find(Message.class, JmsMessageDeliveryState.class);

  @Test
  void replacesDeliveryStateWhenCamelMessageIsRefilled() {
    Message firstJmsMessage = mock(Message.class);
    JmsMessageDeliveryState firstState = new JmsMessageDeliveryState();
    firstState.prepareForReceive();
    assertThat(firstState.claimConsumedMessages()).isTrue();
    JMS_DELIVERY_STATE.set(firstJmsMessage, firstState);

    org.apache.camel.Message camelMessage = mock(org.apache.camel.Message.class);
    JmsMessageInstrumentation.StoreReceiveTelemetryAdvice.onExit(camelMessage, firstJmsMessage);

    JmsMessageDeliveryState camelState = CamelMessageTelemetry.getJmsDeliveryState(camelMessage);
    assertThat(camelState).isSameAs(firstState);
    assertThat(camelState.claimConsumedMessages()).isFalse();

    Message secondJmsMessage = mock(Message.class);
    JmsMessageInstrumentation.StoreReceiveTelemetryAdvice.onExit(camelMessage, secondJmsMessage);

    camelState = CamelMessageTelemetry.getJmsDeliveryState(camelMessage);
    assertThat(camelState).isSameAs(JMS_DELIVERY_STATE.get(secondJmsMessage));
    assertThat(camelState).isNotSameAs(firstState);
    assertThat(camelState.claimConsumedMessages()).isTrue();
  }

  @Test
  void clearsDeliveryStateWhenCamelMessageIsCleared() {
    org.apache.camel.Message camelMessage = mock(org.apache.camel.Message.class);
    JmsMessageInstrumentation.StoreReceiveTelemetryAdvice.onExit(camelMessage, mock(Message.class));
    CamelMessageTelemetry.messageTelemetry().add(camelMessage, RECEIVE, CONSUMED_MESSAGES);

    JmsMessageInstrumentation.StoreReceiveTelemetryAdvice.onExit(camelMessage, null);

    assertThat(CamelMessageTelemetry.getJmsDeliveryState(camelMessage)).isNull();
    assertThat(
            CamelMessageTelemetry.messageTelemetry()
                .contains(camelMessage, RECEIVE, CONSUMED_MESSAGES))
        .isFalse();
  }

  @Test
  void startsNewDeliveryWhenJmsMessageIsWrappedAgain() {
    Message jmsMessage = mock(Message.class);
    org.apache.camel.Message firstCamelMessage = mock(org.apache.camel.Message.class);
    JmsMessageInstrumentation.StoreReceiveTelemetryAdvice.onExit(firstCamelMessage, jmsMessage);
    JmsMessageDeliveryState state = CamelMessageTelemetry.getJmsDeliveryState(firstCamelMessage);
    assertThat(state.claimConsumedMessages()).isTrue();

    org.apache.camel.Message secondCamelMessage = mock(org.apache.camel.Message.class);
    JmsMessageInstrumentation.StoreReceiveTelemetryAdvice.onExit(secondCamelMessage, jmsMessage);

    assertThat(CamelMessageTelemetry.getJmsDeliveryState(secondCamelMessage)).isSameAs(state);
    assertThat(state.claimConsumedMessages()).isTrue();
  }

  @Test
  void copiesDeliveryStateBetweenCamelMessages() {
    org.apache.camel.Message source = mock(org.apache.camel.Message.class);
    org.apache.camel.Message target = mock(org.apache.camel.Message.class);
    Message jmsMessage = mock(Message.class);
    JmsMessageInstrumentation.StoreReceiveTelemetryAdvice.onExit(source, jmsMessage);

    JmsMessageInstrumentation.CopyDeliveryStateAdvice.onExit(target, source);

    assertThat(CamelMessageTelemetry.getJmsDeliveryState(target))
        .isSameAs(CamelMessageTelemetry.getJmsDeliveryState(source));
  }
}
