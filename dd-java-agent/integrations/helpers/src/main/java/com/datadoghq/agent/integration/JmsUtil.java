package com.datadoghq.agent.integration;

import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.Topic;

public class JmsUtil {

  public static String toResourceName(final Message message, final Destination destination) {
    Destination jmsDestination = null;
    try {
      jmsDestination = message.getJMSDestination();
    } catch (final Exception e) {
    }
    if (jmsDestination == null) {
      jmsDestination = destination;
    }
    if (jmsDestination instanceof TemporaryQueue) {
      return "Temporary Queue";
    }
    if (jmsDestination instanceof TemporaryTopic) {
      return "Temporary Topic";
    }
    try {
      if (jmsDestination instanceof Queue) {
        return "Queue " + ((Queue) jmsDestination).getQueueName();
      }
      if (jmsDestination instanceof Topic) {
        return "Topic " + ((Topic) jmsDestination).getTopicName();
      }
    } catch (final Exception e) {
    }
    return "Unknown Destination";
  }
}
