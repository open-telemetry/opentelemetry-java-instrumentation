/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.javaagent.instrumentation.jms.MessageExtractAdapter.GETTER;
import static io.opentelemetry.javaagent.instrumentation.jms.MessageInjectAdapter.SETTER;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.concurrent.TimeUnit;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JmsTracer extends BaseTracer {
  private static final Logger log = LoggerFactory.getLogger(JmsTracer.class);

  // From the spec
  public static final String TEMP_DESTINATION_NAME = "(temporary)";

  private static final JmsTracer TRACER = new JmsTracer();

  public static JmsTracer tracer() {
    return TRACER;
  }

  public Context startConsumerSpan(
      MessageDestination destination, String operation, Message message, long startTime) {
    SpanBuilder spanBuilder =
        tracer
            .spanBuilder(spanName(destination, operation))
            .setSpanKind(CONSUMER)
            .setStartTimestamp(startTime, TimeUnit.MILLISECONDS)
            .setAttribute(SemanticAttributes.MESSAGING_OPERATION, operation);

    Context parentContext = Context.root();
    if (message != null && "process".equals(operation)) {
      // TODO use BaseTracer.extract() which has context leak detection
      //  (and fix the context leak that it is currently detecting when running Jms2Test)
      parentContext =
          GlobalOpenTelemetry.getPropagators()
              .getTextMapPropagator()
              .extract(Context.root(), message, GETTER);
    }
    spanBuilder.setParent(parentContext);

    afterStart(spanBuilder, destination, message);
    return parentContext.with(spanBuilder.startSpan());
  }

  public Context startProducerSpan(MessageDestination destination, Message message) {
    SpanBuilder span = tracer.spanBuilder(spanName(destination, "send")).setSpanKind(PRODUCER);
    afterStart(span, destination, message);
    return Context.current().with(span.startSpan());
  }

  public Scope startProducerScope(Context context, Message message) {
    inject(context, message, SETTER);
    return context.makeCurrent();
  }

  public String spanName(MessageDestination destination, String operation) {
    if (destination.temporary) {
      return TEMP_DESTINATION_NAME + " " + operation;
    } else {
      return destination.destinationName + " " + operation;
    }
  }

  private static final String TIBCO_TMP_PREFIX = "$TMP$";

  public MessageDestination extractDestination(Message message, Destination fallbackDestination) {
    Destination jmsDestination = null;
    try {
      jmsDestination = message.getJMSDestination();
    } catch (Exception ignored) {
    }

    if (jmsDestination == null) {
      jmsDestination = fallbackDestination;
    }

    return extractMessageDestination(jmsDestination);
  }

  public static MessageDestination extractMessageDestination(Destination destination) {
    if (destination instanceof Queue) {
      String queueName;
      try {
        queueName = ((Queue) destination).getQueueName();
      } catch (JMSException e) {
        queueName = "unknown";
      }

      boolean temporary =
          destination instanceof TemporaryQueue || queueName.startsWith(TIBCO_TMP_PREFIX);

      return new MessageDestination(queueName, "queue", temporary);
    }

    if (destination instanceof Topic) {
      String topicName;
      try {
        topicName = ((Topic) destination).getTopicName();
      } catch (JMSException e) {
        topicName = "unknown";
      }

      boolean temporary =
          destination instanceof TemporaryTopic || topicName.startsWith(TIBCO_TMP_PREFIX);

      return new MessageDestination(topicName, "topic", temporary);
    }

    return MessageDestination.UNKNOWN;
  }

  private void afterStart(SpanBuilder span, MessageDestination destination, Message message) {
    span.setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "jms");
    span.setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND, destination.destinationKind);
    if (destination.temporary) {
      span.setAttribute(SemanticAttributes.MESSAGING_TEMP_DESTINATION, true);
      span.setAttribute(SemanticAttributes.MESSAGING_DESTINATION, TEMP_DESTINATION_NAME);
    } else {
      span.setAttribute(SemanticAttributes.MESSAGING_DESTINATION, destination.destinationName);
    }

    if (message != null) {
      try {
        String messageId = message.getJMSMessageID();
        if (messageId != null) {
          span.setAttribute(SemanticAttributes.MESSAGING_MESSAGE_ID, messageId);
        }
      } catch (Exception e) {
        log.debug("Failure getting JMS message id", e);
      }

      try {
        String correlationId = message.getJMSCorrelationID();
        if (correlationId != null) {
          span.setAttribute(SemanticAttributes.MESSAGING_CONVERSATION_ID, correlationId);
        }
      } catch (Exception e) {
        log.debug("Failure getting JMS correlation id", e);
      }
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.jms-1.1";
  }
}
