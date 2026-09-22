/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.jms.JmsMessageProcessingState;
import javax.jms.Message;
import javax.jms.MessageListener;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.junit.jupiter.api.Test;

class SjmsProcessingLifecycleTest {

  private static final VirtualField<Message, JmsMessageProcessingState> PROCESSING_STATE =
      VirtualField.find(Message.class, JmsMessageProcessingState.class);
  private static final VirtualField<MessageListener, Boolean> PROCESSING_SELECTION =
      VirtualField.find(MessageListener.class, Boolean.class);

  @Test
  void selectsCamelProcessingForSjmsListener() {
    MessageListener listener = mock(MessageListener.class);

    SjmsConsumerInstrumentation.CreateMessageHandlerAdvice.onExit(
        consumerWithCoreInstrumentation(true), listener);

    assertThat(PROCESSING_SELECTION.get(listener))
        .isEqualTo(emitStableMessagingSemconv() ? Boolean.TRUE : null);
  }

  @Test
  void doesNotSelectCamelProcessingWithoutCoreInstrumentation() {
    MessageListener listener = mock(MessageListener.class);

    SjmsConsumerInstrumentation.CreateMessageHandlerAdvice.onExit(
        consumerWithCoreInstrumentation(false), listener);

    assertThat(PROCESSING_SELECTION.get(listener)).isNull();
  }

  @Test
  void replacesCompletedStateAtSjmsCallbackBoundary() {
    Message jmsMessage = mock(Message.class);

    JmsMessageProcessingState firstState =
        SjmsMessageHandlerInstrumentation.MessageHandlerAdvice.onEnter(jmsMessage);
    assertThat(PROCESSING_STATE.get(jmsMessage)).isSameAs(firstState);
    assertThat(firstState.beginProcessing()).isFalse();
    assertThat(firstState.endProcessing()).isFalse();
    SjmsMessageHandlerInstrumentation.MessageHandlerAdvice.onExit(firstState);
    assertThat(firstState.isProcessingCompleted()).isTrue();

    JmsMessageProcessingState secondState =
        SjmsMessageHandlerInstrumentation.MessageHandlerAdvice.onEnter(jmsMessage);
    assertThat(secondState).isSameAs(PROCESSING_STATE.get(jmsMessage)).isNotSameAs(firstState);
    SjmsMessageHandlerInstrumentation.MessageHandlerAdvice.onExit(secondState);
    assertThat(secondState.isProcessingCompleted()).isTrue();
  }

  private static Consumer consumerWithCoreInstrumentation(boolean enabled) {
    Consumer consumer = mock(Consumer.class);
    Endpoint endpoint = mock(Endpoint.class);
    CamelContext camelContext = mock(CamelContext.class);
    when(consumer.getEndpoint()).thenReturn(endpoint);
    when(endpoint.getCamelContext()).thenReturn(camelContext);
    if (enabled) {
      CamelInstrumentationEnabled.markEnabled(camelContext);
    }
    return consumer;
  }
}
