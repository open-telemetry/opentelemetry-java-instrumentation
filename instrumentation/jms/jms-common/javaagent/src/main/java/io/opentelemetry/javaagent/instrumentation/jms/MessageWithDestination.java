/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class MessageWithDestination {

  // visible for tests
  static final String TIBCO_TMP_PREFIX = "$TMP$";

  public abstract MessageAdapter message();

  public abstract String destinationName();

  public abstract String destinationKind();

  public abstract boolean isTemporaryDestination();

  public static MessageWithDestination create(
      MessageAdapter message, DestinationAdapter fallbackDestination) {
    DestinationAdapter jmsDestination = null;
    try {
      jmsDestination = message.getJmsDestination();
    } catch (Exception ignored) {
      // Ignore
    }
    if (jmsDestination == null) {
      jmsDestination = fallbackDestination;
    }

    if (jmsDestination.isQueue()) {
      return createMessageWithQueue(message, jmsDestination);
    }
    if (jmsDestination.isTopic()) {
      return createMessageWithTopic(message, jmsDestination);
    }
    return new AutoValue_MessageWithDestination(
        message, "unknown", "unknown", /* isTemporaryDestination= */ false);
  }

  private static MessageWithDestination createMessageWithQueue(
      MessageAdapter message, DestinationAdapter queue) {
    String queueName;
    try {
      queueName = queue.getQueueName();
    } catch (Exception e) {
      queueName = "unknown";
    }

    boolean temporary = queue.isTemporaryQueue() || queueName.startsWith(TIBCO_TMP_PREFIX);

    return new AutoValue_MessageWithDestination(message, queueName, "queue", temporary);
  }

  private static MessageWithDestination createMessageWithTopic(
      MessageAdapter message, DestinationAdapter topic) {
    String topicName;
    try {
      topicName = topic.getTopicName();
    } catch (Exception e) {
      topicName = "unknown";
    }

    boolean temporary = topic.isTemporaryTopic() || topicName.startsWith(TIBCO_TMP_PREFIX);

    return new AutoValue_MessageWithDestination(message, topicName, "topic", temporary);
  }
}
