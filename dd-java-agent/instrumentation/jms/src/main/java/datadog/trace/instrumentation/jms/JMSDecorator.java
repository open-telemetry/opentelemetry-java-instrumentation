package datadog.trace.instrumentation.jms;

import datadog.trace.agent.decorator.ClientDecorator;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
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
        protected String spanKind() {
          return Tags.SPAN_KIND_PRODUCER;
        }

        @Override
        protected String spanType() {
          return DDSpanTypes.MESSAGE_PRODUCER;
        }
      };

  public static final JMSDecorator CONSUMER_DECORATE =
      new JMSDecorator() {
        @Override
        protected String spanKind() {
          return Tags.SPAN_KIND_CONSUMER;
        }

        @Override
        protected String spanType() {
          return DDSpanTypes.MESSAGE_CONSUMER;
        }
      };

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"jms", "jms-1", "jms-2"};
  }

  @Override
  protected String service() {
    return "jms";
  }

  @Override
  protected String component() {
    return "jms";
  }

  @Override
  protected abstract String spanKind();

  public void onConsume(final Span span, final Message message) {
    span.setTag(DDTags.RESOURCE_NAME, "Consumed from " + toResourceName(message, null));
  }

  public void onReceive(final Span span, final Method method) {
    span.setTag(DDTags.RESOURCE_NAME, "JMS " + method.getName());
  }

  public void onReceive(final Scope scope, final Message message) {
    scope.span().setTag(DDTags.RESOURCE_NAME, "Received from " + toResourceName(message, null));
  }

  public void onProduce(final Scope scope, final Message message, final Destination destination) {
    scope
        .span()
        .setTag(DDTags.RESOURCE_NAME, "Produced for " + toResourceName(message, destination));
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
