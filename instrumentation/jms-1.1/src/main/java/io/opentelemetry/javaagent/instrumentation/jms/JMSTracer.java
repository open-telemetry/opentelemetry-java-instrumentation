/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import static io.opentelemetry.api.trace.Span.Kind.CONSUMER;
import static io.opentelemetry.api.trace.Span.Kind.PRODUCER;
import static io.opentelemetry.instrumentation.api.decorator.BaseDecorator.extract;
import static io.opentelemetry.javaagent.instrumentation.jms.MessageExtractAdapter.GETTER;
import static io.opentelemetry.javaagent.instrumentation.jms.MessageInjectAdapter.SETTER;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.attributes.SemanticAttributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
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

public class JMSTracer extends BaseTracer {
  private static final Logger log = LoggerFactory.getLogger(JMSTracer.class);

  // From the spec
  public static final String TEMP_DESTINATION_NAME = "(temporary)";

  private static final JMSTracer TRACER = new JMSTracer();

  public static JMSTracer tracer() {
    return TRACER;
  }

  public Span startConsumerSpan(
      MessageDestination destination, String operation, Message message, long startTime) {
    Span.Builder spanBuilder =
        tracer
            .spanBuilder(spanName(destination, operation))
            .setSpanKind(CONSUMER)
            .setStartTimestamp(TimeUnit.MILLISECONDS.toNanos(startTime))
            .setAttribute(SemanticAttributes.MESSAGING_OPERATION, operation);

    if (message != null && "process".equals(operation)) {
      Context context = extract(message, GETTER);
      SpanContext spanContext = Span.fromContext(context).getSpanContext();
      if (spanContext.isValid()) {
        spanBuilder.setParent(context);
      }
    }

    Span span = spanBuilder.startSpan();
    afterStart(span, destination, message);
    return span;
  }

  public Span startProducerSpan(MessageDestination destination, Message message) {
    Span span = tracer.spanBuilder(spanName(destination, "send")).setSpanKind(PRODUCER).startSpan();
    afterStart(span, destination, message);
    return span;
  }

  public Scope startProducerScope(Span span, Message message) {
    Context context = Context.current().with(span);
    OpenTelemetry.getGlobalPropagators().getTextMapPropagator().inject(context, message, SETTER);
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

  private void afterStart(Span span, MessageDestination destination, Message message) {
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

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.jms";
  }
}
