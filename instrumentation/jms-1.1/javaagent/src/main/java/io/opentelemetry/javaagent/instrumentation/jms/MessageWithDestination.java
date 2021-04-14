/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.Topic;

public final class MessageWithDestination {
  private static final String TIBCO_TMP_PREFIX = "$TMP$";

  private final Message message;
  private final MessageOperation messageOperation;
  private final String destinationName;
  private final String destinationKind;
  private final boolean temporaryDestination;

  MessageWithDestination(
      Message message,
      MessageOperation messageOperation,
      String destinationName,
      String destinationKind,
      boolean temporary) {
    this.message = message;
    this.messageOperation = messageOperation;
    this.destinationName = destinationName;
    this.destinationKind = destinationKind;
    this.temporaryDestination = temporary;
  }

  public Message getMessage() {
    return message;
  }

  public MessageOperation getMessageOperation() {
    return messageOperation;
  }

  public String getDestinationName() {
    return destinationName;
  }

  public String getDestinationKind() {
    return destinationKind;
  }

  public boolean isTemporaryDestination() {
    return temporaryDestination;
  }

  public static MessageWithDestination create(
      Message message, MessageOperation operation, Destination fallbackDestination) {
    Destination jmsDestination = null;
    try {
      jmsDestination = message.getJMSDestination();
    } catch (Exception ignored) {
    }
    if (jmsDestination == null) {
      jmsDestination = fallbackDestination;
    }

    if (jmsDestination instanceof Queue) {
      return createMessageWithQueue(message, operation, (Queue) jmsDestination);
    }
    if (jmsDestination instanceof Topic) {
      return createMessageWithTopic(message, operation, (Topic) jmsDestination);
    }
    return new MessageWithDestination(message, operation, "unknown", "unknown", false);
  }

  private static MessageWithDestination createMessageWithQueue(
      Message message, MessageOperation operation, Queue destination) {
    String queueName;
    try {
      queueName = destination.getQueueName();
    } catch (JMSException e) {
      queueName = "unknown";
    }

    boolean temporary =
        destination instanceof TemporaryQueue || queueName.startsWith(TIBCO_TMP_PREFIX);

    return new MessageWithDestination(message, operation, queueName, "queue", temporary);
  }

  private static MessageWithDestination createMessageWithTopic(
      Message message, MessageOperation operation, Topic destination) {
    String topicName;
    try {
      topicName = destination.getTopicName();
    } catch (JMSException e) {
      topicName = "unknown";
    }

    boolean temporary =
        destination instanceof TemporaryTopic || topicName.startsWith(TIBCO_TMP_PREFIX);

    return new MessageWithDestination(message, operation, topicName, "topic", temporary);
  }
}
