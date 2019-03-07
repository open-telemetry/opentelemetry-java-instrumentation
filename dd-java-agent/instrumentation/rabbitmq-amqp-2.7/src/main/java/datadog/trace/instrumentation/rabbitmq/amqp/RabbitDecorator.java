package datadog.trace.instrumentation.rabbitmq.amqp;

import com.rabbitmq.client.Command;
import com.rabbitmq.client.Envelope;
import datadog.trace.agent.decorator.ClientDecorator;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;

public class RabbitDecorator extends ClientDecorator {
  public static final RabbitDecorator DECORATE = new RabbitDecorator();

  public static final RabbitDecorator PRODUCER_DECORATE =
      new RabbitDecorator() {
        @Override
        protected String spanKind() {
          return Tags.SPAN_KIND_PRODUCER;
        }

        @Override
        protected String spanType() {
          return DDSpanTypes.MESSAGE_PRODUCER;
        }
      };

  public static final RabbitDecorator CONSUMER_DECORATE =
      new RabbitDecorator() {
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
    return new String[] {"amqp", "rabbitmq"};
  }

  @Override
  protected String service() {
    return "rabbitmq";
  }

  @Override
  protected String component() {
    return "rabbitmq-amqp";
  }

  @Override
  protected String spanKind() {
    return Tags.SPAN_KIND_CLIENT;
  }

  @Override
  protected String spanType() {
    return DDSpanTypes.MESSAGE_CLIENT;
  }

  public void onPublish(final Span span, final String exchange, final String routingKey) {
    final String exchangeName = exchange == null || exchange.isEmpty() ? "<default>" : exchange;
    final String routing =
        routingKey == null || routingKey.isEmpty()
            ? "<all>"
            : routingKey.startsWith("amq.gen-") ? "<generated>" : routingKey;
    span.setTag(DDTags.RESOURCE_NAME, "basic.publish " + exchangeName + " -> " + routing);
    span.setTag(DDTags.SPAN_TYPE, DDSpanTypes.MESSAGE_PRODUCER);
    span.setTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_PRODUCER);
    span.setTag("amqp.command", "basic.publish");
    span.setTag("amqp.exchange", exchange);
    span.setTag("amqp.routing_key", routingKey);
  }

  public void onGet(final Span span, final String queue) {
    final String queueName = queue.startsWith("amq.gen-") ? "<generated>" : queue;
    span.setTag(DDTags.RESOURCE_NAME, "basic.get " + queueName);

    span.setTag("amqp.command", "basic.get");
    span.setTag("amqp.queue", queue);
  }

  public void onDeliver(final Scope scope, final String queue, final Envelope envelope) {
    final Span span = scope.span();

    String queueName = queue;
    if (queue == null || queue.isEmpty()) {
      queueName = "<default>";
    } else if (queue.startsWith("amq.gen-")) {
      queueName = "<generated>";
    }
    span.setTag(DDTags.RESOURCE_NAME, "basic.deliver " + queueName);
    span.setTag("amqp.command", "basic.deliver");

    if (envelope != null) {
      span.setTag("amqp.exchange", envelope.getExchange());
      span.setTag("amqp.routing_key", envelope.getRoutingKey());
    }
  }

  public void onCommand(final Span span, final Command command) {
    final String name = command.getMethod().protocolMethodName();

    if (!name.equals("basic.publish")) {
      // Don't overwrite the name already set.
      span.setTag(DDTags.RESOURCE_NAME, name);
    }
    span.setTag("amqp.command", name);
  }
}
