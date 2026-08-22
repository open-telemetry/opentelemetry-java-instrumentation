/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20.decorators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MessagingMessageIdTest {

  @ParameterizedTest
  @MethodSource("jmsComponents")
  void jmsMessageId(String component, String system, boolean legacyMessageIdExpected) {
    MessagingSpanDecorator decorator = new MessagingSpanDecorator(component, system, true);
    Exchange exchange = mock(Exchange.class);
    Message message = mock(Message.class);
    when(exchange.getIn()).thenReturn(message);
    when(message.getHeader("JMSMessageID")).thenReturn("ID:123");

    assertThat(decorator.getMessageId(exchange))
        .isEqualTo(legacyMessageIdExpected ? "ID:123" : null);
    assertThat(decorator.getStableMessageId(exchange)).isEqualTo("ID:123");
  }

  private static Stream<Arguments> jmsComponents() {
    return Stream.of(
        argumentSet("JMS", "jms", "jms", true),
        argumentSet("simple JMS", "sjms", "jms", false),
        argumentSet("AMQP", "amqp", "amqp", false),
        argumentSet("legacy AMQP alias", "ampq", "amqp", false));
  }
}
