/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.amqp;

import com.rabbitmq.client.Command;
import com.rabbitmq.client.Envelope;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.instrumentation.api.decorator.ClientDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

public class RabbitDecorator extends ClientDecorator {

  public static final RabbitDecorator DECORATE = new RabbitDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracer("io.opentelemetry.auto.rabbitmq-amqp-2.7");

  public void onPublish(Span span, String exchange, String routingKey) {
    String exchangeName = exchange == null || exchange.isEmpty() ? "<default>" : exchange;
    String routing =
        routingKey == null || routingKey.isEmpty()
            ? "<all>"
            : routingKey.startsWith("amq.gen-") ? "<generated>" : routingKey;
    span.updateName(exchangeName + " -> " + routing);
    span.setAttribute("amqp.command", "basic.publish");
    if (exchange != null && !exchange.isEmpty()) {
      span.setAttribute("amqp.exchange", exchange);
    }
    if (routingKey != null && !routingKey.isEmpty()) {
      span.setAttribute("amqp.routing_key", routingKey);
    }
  }

  public String spanNameOnGet(String queue) {
    return queue.startsWith("amq.gen-") ? "<generated>" : queue;
  }

  public void onGet(Span span, String queue) {
    span.setAttribute("amqp.command", "basic.get");
    span.setAttribute("amqp.queue", queue);
  }

  public String spanNameOnDeliver(String queue) {
    if (queue == null || queue.isEmpty()) {
      return "<default>";
    } else if (queue.startsWith("amq.gen-")) {
      return "<generated>";
    } else {
      return queue;
    }
  }

  public void onDeliver(Span span, Envelope envelope) {
    span.setAttribute("amqp.command", "basic.deliver");

    if (envelope != null) {
      String exchange = envelope.getExchange();
      if (exchange != null && !exchange.isEmpty()) {
        span.setAttribute("amqp.exchange", exchange);
      }
      String routingKey = envelope.getRoutingKey();
      if (routingKey != null && !routingKey.isEmpty()) {
        span.setAttribute("amqp.routing_key", routingKey);
      }
    }
  }

  public void onCommand(Span span, Command command) {
    String name = command.getMethod().protocolMethodName();

    if (!name.equals("basic.publish")) {
      span.updateName(name);
    }
    span.setAttribute("amqp.command", name);
  }
}
