/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.instrumentation.api.decorator.ClientDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JMSDecorator extends ClientDecorator {
  private static final Logger log = LoggerFactory.getLogger(JMSDecorator.class);

  public static final JMSDecorator DECORATE = new JMSDecorator();

  public static final Tracer TRACER = OpenTelemetry.getTracer("io.opentelemetry.auto.jms-1.1");

  public String spanNameForConsumer(Message message) {
    return toSpanName(message, null, "receive");
  }

  public String spanNameForProducer(Message message, Destination destination) {
    return toSpanName(message, destination, "send");
  }

  private static final String TIBCO_TMP_PREFIX = "$TMP$";

  public static String toSpanName(Message message, Destination destination, String operationName) {
    Destination jmsDestination = null;
    try {
      jmsDestination = message.getJMSDestination();
    } catch (Exception e) {
    }
    if (jmsDestination == null) {
      jmsDestination = destination;
    }
    return toSpanName(jmsDestination, operationName);
  }

  public static String toSpanName(Destination destination, String operationName) {
    try {
      if (destination instanceof Queue) {
        String queueName = ((Queue) destination).getQueueName();
        if (destination instanceof TemporaryQueue || queueName.startsWith(TIBCO_TMP_PREFIX)) {
          return "queue/<temporary> " + operationName;
        } else {
          return "queue/" + queueName + " " + operationName;
        }
      }
      if (destination instanceof Topic) {
        String topicName = ((Topic) destination).getTopicName();
        if (destination instanceof TemporaryTopic || topicName.startsWith(TIBCO_TMP_PREFIX)) {
          return "topic/<temporary> " + operationName;
        } else {
          return "topic/" + topicName + " " + operationName;
        }
      }
    } catch (Exception e) {
    }
    return "destination";
  }

  public void afterStart(Span span, String spanName, Message message) {
    super.afterStart(span);
    if (spanName.startsWith("queue/")) {
      span.setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND, "queue");
      span.setAttribute(
          SemanticAttributes.MESSAGING_DESTINATION,
          spanName.replaceFirst("^queue/", "").replaceFirst(" (send|receive)$", ""));
    } else if (spanName.startsWith("topic/")) {
      span.setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic");
      span.setAttribute(
          SemanticAttributes.MESSAGING_DESTINATION,
          spanName.replaceFirst("^topic/", "").replaceFirst(" (send|receive)$", ""));
    }
    if (spanName.startsWith("queue/<temporary>") || spanName.startsWith("topic/<temporary>")) {
      span.setAttribute(SemanticAttributes.MESSAGING_TEMP_DESTINATION, true);
    }

    if (message != null) {
      try {
        String messageID = message.getJMSMessageID();
        if (messageID != null) {
          span.setAttribute(SemanticAttributes.MESSAGING_MESSAGE_ID, messageID);
        }
      } catch (Exception e) {
        log.debug("Failure getting JMS message id", e);
      }

      try {
        String correlationID = message.getJMSCorrelationID();
        if (correlationID != null) {
          span.setAttribute(SemanticAttributes.MESSAGING_CONVERSATION_ID, correlationID);
        }
      } catch (Exception e) {
        log.debug("Failure getting JMS correlation id", e);
      }
    }
  }
}
