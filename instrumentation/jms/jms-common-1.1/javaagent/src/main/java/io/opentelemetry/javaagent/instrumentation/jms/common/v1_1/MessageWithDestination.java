/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.common.v1_1;

import static java.util.logging.Level.FINE;

import com.google.auto.value.AutoValue;
import java.util.logging.Logger;
import javax.annotation.Nullable;

@AutoValue
public abstract class MessageWithDestination {

  private static final Logger logger = Logger.getLogger(MessageWithDestination.class.getName());

  // visible for tests
  static final String TIBCO_TMP_PREFIX = "$TMP$";

  public abstract MessageAdapter message();

  @Nullable
  public abstract String destinationName();

  public abstract boolean isTemporaryDestination();

  @Nullable
  public abstract String destinationSubscriptionName();

  public static MessageWithDestination create(
      MessageAdapter message, @Nullable DestinationAdapter fallbackDestination) {
    return create(message, fallbackDestination, null);
  }

  public static MessageWithDestination create(
      MessageAdapter message,
      @Nullable DestinationAdapter fallbackDestination,
      @Nullable String destinationSubscriptionName) {
    DestinationAdapter jmsDestination = null;
    try {
      jmsDestination = message.getJmsDestination();
    } catch (Exception e) {
      logger.log(FINE, "Failure getting JMS destination", e);
    }
    if (jmsDestination == null) {
      jmsDestination = fallbackDestination;
    }

    if (jmsDestination != null) {
      if (jmsDestination.isQueue()) {
        return createMessageWithQueue(message, jmsDestination, destinationSubscriptionName);
      }
      if (jmsDestination.isTopic()) {
        return createMessageWithTopic(message, jmsDestination, destinationSubscriptionName);
      }
    }
    return new AutoValue_MessageWithDestination(
        message, null, /* isTemporaryDestination= */ false, destinationSubscriptionName);
  }

  private static MessageWithDestination createMessageWithQueue(
      MessageAdapter message,
      DestinationAdapter queue,
      @Nullable String destinationSubscriptionName) {

    String queueName = getDestinationName(queue, DestinationAdapter::getQueueName);
    boolean temporary =
        queue.isTemporaryQueue() || (queueName != null && queueName.startsWith(TIBCO_TMP_PREFIX));

    return new AutoValue_MessageWithDestination(
        message, queueName, temporary, destinationSubscriptionName);
  }

  private static MessageWithDestination createMessageWithTopic(
      MessageAdapter message,
      DestinationAdapter topic,
      @Nullable String destinationSubscriptionName) {

    String topicName = getDestinationName(topic, DestinationAdapter::getTopicName);
    boolean temporary =
        topic.isTemporaryTopic() || (topicName != null && topicName.startsWith(TIBCO_TMP_PREFIX));

    return new AutoValue_MessageWithDestination(
        message, topicName, temporary, destinationSubscriptionName);
  }

  @Nullable
  private static String getDestinationName(DestinationAdapter destination, NameGetter nameGetter) {
    try {
      return nameGetter.getName(destination);
    } catch (Exception e) {
      logger.log(FINE, "Failure getting JMS destination name", e);
      return null;
    }
  }

  @FunctionalInterface
  private interface NameGetter {

    String getName(DestinationAdapter destination) throws Exception;
  }
}
