package io.opentelemetry.auto.instrumentation.jms;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.ClientDecorator;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.SpanTypes;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.lang.reflect.Method;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.Topic;

public abstract class JMSDecorator extends ClientDecorator {
  public static final JMSDecorator PRODUCER_DECORATE =
      new JMSDecorator() {
        @Override
        protected String getSpanType() {
          return SpanTypes.MESSAGE_PRODUCER;
        }
      };

  public static final JMSDecorator CONSUMER_DECORATE =
      new JMSDecorator() {
        @Override
        protected String getSpanType() {
          return SpanTypes.MESSAGE_CONSUMER;
        }
      };

  public static final Tracer TRACER = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  @Override
  protected String service() {
    return "jms";
  }

  @Override
  protected String getComponentName() {
    return "jms";
  }

  public void onConsume(final Span span, final Message message) {
    span.setAttribute(MoreTags.RESOURCE_NAME, "Consumed from " + toResourceName(message, null));
  }

  public void onReceive(final Span span, final Method method) {
    span.setAttribute(MoreTags.RESOURCE_NAME, "JMS " + method.getName());
  }

  public void onReceive(final Span span, final Message message) {
    span.setAttribute(MoreTags.RESOURCE_NAME, "Received from " + toResourceName(message, null));
  }

  public void onProduce(final Span span, final Message message, final Destination destination) {
    span.setAttribute(
        MoreTags.RESOURCE_NAME, "Produced for " + toResourceName(message, destination));
  }

  private static final String TIBCO_TMP_PREFIX = "$TMP$";

  public static String toResourceName(final Message message, final Destination destination) {
    Destination jmsDestination = null;
    try {
      jmsDestination = message.getJMSDestination();
    } catch (final Exception e) {
    }
    if (jmsDestination == null) {
      jmsDestination = destination;
    }
    try {
      if (jmsDestination instanceof Queue) {
        final String queueName = ((Queue) jmsDestination).getQueueName();
        if (jmsDestination instanceof TemporaryQueue || queueName.startsWith(TIBCO_TMP_PREFIX)) {
          return "Temporary Queue";
        } else {
          return "Queue " + queueName;
        }
      }
      if (jmsDestination instanceof Topic) {
        final String topicName = ((Topic) jmsDestination).getTopicName();
        if (jmsDestination instanceof TemporaryTopic || topicName.startsWith(TIBCO_TMP_PREFIX)) {
          return "Temporary Topic";
        } else {
          return "Topic " + topicName;
        }
      }
    } catch (final Exception e) {
    }
    return "Destination";
  }
}
