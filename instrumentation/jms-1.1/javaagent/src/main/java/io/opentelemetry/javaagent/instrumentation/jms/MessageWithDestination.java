/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import java.time.Instant;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.Topic;

@AutoValue
public abstract class MessageWithDestination {

  // visible for tests
  static final String TIBCO_TMP_PREFIX = "$TMP$";

  public abstract Message message();

  public abstract MessageOperation messageOperation();

  public abstract String destinationName();

  public abstract String destinationKind();

  public abstract boolean isTemporaryDestination();

  abstract Timer timer();

  public Instant startTime() {
    return timer().startTime();
  }

  public Instant endTime() {
    return timer().endTime();
  }

  public static MessageWithDestination create(
      Message message, MessageOperation operation, Destination fallbackDestination) {
    return create(message, operation, fallbackDestination, Timer.start());
  }

  public static MessageWithDestination create(
      Message message, MessageOperation operation, Destination fallbackDestination, Timer timer) {
    Destination jmsDestination = null;
    try {
      jmsDestination = message.getJMSDestination();
    } catch (Exception ignored) {
      // Ignore
    }
    if (jmsDestination == null) {
      jmsDestination = fallbackDestination;
    }

    if (jmsDestination instanceof Queue) {
      return createMessageWithQueue(message, operation, (Queue) jmsDestination, timer);
    }
    if (jmsDestination instanceof Topic) {
      return createMessageWithTopic(message, operation, (Topic) jmsDestination, timer);
    }
    return new AutoValue_MessageWithDestination(
        message, operation, "unknown", "unknown", /* isTemporaryDestination= */ false, timer);
  }

  private static MessageWithDestination createMessageWithQueue(
      Message message, MessageOperation operation, Queue destination, Timer timer) {
    String queueName;
    try {
      queueName = destination.getQueueName();
    } catch (JMSException e) {
      queueName = "unknown";
    }

    boolean temporary =
        destination instanceof TemporaryQueue || queueName.startsWith(TIBCO_TMP_PREFIX);

    return new AutoValue_MessageWithDestination(
        message, operation, queueName, "queue", temporary, timer);
  }

  private static MessageWithDestination createMessageWithTopic(
      Message message, MessageOperation operation, Topic destination, Timer timer) {
    String topicName;
    try {
      topicName = destination.getTopicName();
    } catch (JMSException e) {
      topicName = "unknown";
    }

    boolean temporary =
        destination instanceof TemporaryTopic || topicName.startsWith(TIBCO_TMP_PREFIX);

    return new AutoValue_MessageWithDestination(
        message, operation, topicName, "topic", temporary, timer);
  }
}
