package io.opentelemetry.auto.instrumentation.rabbitmq.amqp;

import com.rabbitmq.client.Command;
import com.rabbitmq.client.Envelope;
import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.api.SpanTypes;
import io.opentelemetry.auto.decorator.ClientDecorator;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import io.opentelemetry.auto.instrumentation.api.Tags;

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
          return SpanTypes.MESSAGE_PRODUCER;
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
          return SpanTypes.MESSAGE_CONSUMER;
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
    return SpanTypes.MESSAGE_CLIENT;
  }

  public void onPublish(final AgentSpan span, final String exchange, final String routingKey) {
    final String exchangeName = exchange == null || exchange.isEmpty() ? "<default>" : exchange;
    final String routing =
        routingKey == null || routingKey.isEmpty()
            ? "<all>"
            : routingKey.startsWith("amq.gen-") ? "<generated>" : routingKey;
    span.setAttribute(MoreTags.RESOURCE_NAME, "basic.publish " + exchangeName + " -> " + routing);
    span.setAttribute(MoreTags.SPAN_TYPE, SpanTypes.MESSAGE_PRODUCER);
    span.setAttribute(Tags.SPAN_KIND, Tags.SPAN_KIND_PRODUCER);
    span.setAttribute("amqp.command", "basic.publish");
    span.setAttribute("amqp.exchange", exchange);
    span.setAttribute("amqp.routing_key", routingKey);
  }

  public void onGet(final AgentSpan span, final String queue) {
    final String queueName = queue.startsWith("amq.gen-") ? "<generated>" : queue;
    span.setAttribute(MoreTags.RESOURCE_NAME, "basic.get " + queueName);

    span.setAttribute("amqp.command", "basic.get");
    span.setAttribute("amqp.queue", queue);
  }

  public void onDeliver(final AgentSpan span, final String queue, final Envelope envelope) {
    String queueName = queue;
    if (queue == null || queue.isEmpty()) {
      queueName = "<default>";
    } else if (queue.startsWith("amq.gen-")) {
      queueName = "<generated>";
    }
    span.setAttribute(MoreTags.RESOURCE_NAME, "basic.deliver " + queueName);
    span.setAttribute("amqp.command", "basic.deliver");

    if (envelope != null) {
      span.setAttribute("amqp.exchange", envelope.getExchange());
      span.setAttribute("amqp.routing_key", envelope.getRoutingKey());
    }
  }

  public void onCommand(final AgentSpan span, final Command command) {
    final String name = command.getMethod().protocolMethodName();

    if (!name.equals("basic.publish")) {
      // Don't overwrite the name already set.
      span.setAttribute(MoreTags.RESOURCE_NAME, name);
    }
    span.setAttribute("amqp.command", name);
  }
}
