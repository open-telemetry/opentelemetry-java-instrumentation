/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import static io.opentelemetry.javaagent.instrumentation.jms.MessageWithDestination.TIBCO_TMP_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.Topic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageWithDestinationTest {

  @Mock Message message;
  @Mock Topic topic;
  @Mock TemporaryTopic temporaryTopic;
  @Mock Queue queue;
  @Mock TemporaryQueue temporaryQueue;
  @Mock Destination destination;

  @Test
  void shouldCreateMessageWithUnknownDestination() throws JMSException {
    // given
    when(message.getJMSDestination()).thenReturn(destination);

    // when
    MessageWithDestination result = MessageWithDestination.create(message, null);

    // then
    assertMessage("unknown", "unknown", /* expectedTemporary= */ false, result);
  }

  @Test
  void shouldUseFallbackDestinationToCreateMessage() throws JMSException {
    // given
    when(message.getJMSDestination()).thenThrow(JMSException.class);

    // when
    MessageWithDestination result = MessageWithDestination.create(message, destination);

    // then
    assertMessage("unknown", "unknown", /* expectedTemporary= */ false, result);
  }

  @ParameterizedTest
  @MethodSource("destinations")
  void shouldCreateMessageWithQueue(
      String queueName,
      boolean useTemporaryDestination,
      String expectedDestinationName,
      boolean expectedTemporary)
      throws JMSException {
    // given
    Queue queue = useTemporaryDestination ? this.temporaryQueue : this.queue;

    when(message.getJMSDestination()).thenReturn(queue);
    if (queueName == null) {
      when(queue.getQueueName()).thenThrow(JMSException.class);
    } else {
      when(queue.getQueueName()).thenReturn(queueName);
    }

    // when
    MessageWithDestination result = MessageWithDestination.create(message, null);

    // then
    assertMessage("queue", expectedDestinationName, expectedTemporary, result);
  }

  @ParameterizedTest
  @MethodSource("destinations")
  void shouldCreateMessageWithTopic(
      String topicName,
      boolean useTemporaryDestination,
      String expectedDestinationName,
      boolean expectedTemporary)
      throws JMSException {
    // given
    Topic topic = useTemporaryDestination ? this.temporaryTopic : this.topic;

    when(message.getJMSDestination()).thenReturn(topic);
    if (topicName == null) {
      when(topic.getTopicName()).thenThrow(JMSException.class);
    } else {
      when(topic.getTopicName()).thenReturn(topicName);
    }

    // when
    MessageWithDestination result = MessageWithDestination.create(message, null);

    // then
    assertMessage("topic", expectedDestinationName, expectedTemporary, result);
  }

  static Stream<Arguments> destinations() {
    return Stream.of(
        Arguments.of("destination", false, "destination", false),
        Arguments.of(null, false, "unknown", false),
        Arguments.of(TIBCO_TMP_PREFIX + "dest", false, TIBCO_TMP_PREFIX + "dest", true),
        Arguments.of("destination", true, "destination", true));
  }

  private void assertMessage(
      String expectedDestinationKind,
      String expectedDestinationName,
      boolean expectedTemporary,
      MessageWithDestination actual) {

    assertSame(message, actual.message());
    assertEquals(expectedDestinationKind, actual.destinationKind());
    assertEquals(expectedDestinationName, actual.destinationName());
    assertEquals(expectedTemporary, actual.isTemporaryDestination());
  }
}
