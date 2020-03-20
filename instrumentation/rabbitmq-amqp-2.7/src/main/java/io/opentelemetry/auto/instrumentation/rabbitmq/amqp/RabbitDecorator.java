/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.rabbitmq.amqp;

import com.rabbitmq.client.Command;
import com.rabbitmq.client.Envelope;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.ClientDecorator;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

public class RabbitDecorator extends ClientDecorator {

  public static final RabbitDecorator DECORATE = new RabbitDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.rabbitmq-amqp-2.7");

  @Override
  protected String service() {
    return "rabbitmq";
  }

  @Override
  protected String getComponentName() {
    return "rabbitmq-amqp";
  }

  public void onPublish(final Span span, final String exchange, final String routingKey) {
    final String exchangeName = exchange == null || exchange.isEmpty() ? "<default>" : exchange;
    final String routing =
        routingKey == null || routingKey.isEmpty()
            ? "<all>"
            : routingKey.startsWith("amq.gen-") ? "<generated>" : routingKey;
    span.setAttribute(MoreTags.RESOURCE_NAME, "basic.publish " + exchangeName + " -> " + routing);
    span.setAttribute("amqp.command", "basic.publish");
    if (exchange != null && !exchange.isEmpty()) {
      span.setAttribute("amqp.exchange", exchange);
    }
    if (routingKey != null && !routingKey.isEmpty()) {
      span.setAttribute("amqp.routing_key", routingKey);
    }
  }

  public void onGet(final Span span, final String queue) {
    final String queueName = queue.startsWith("amq.gen-") ? "<generated>" : queue;
    span.setAttribute(MoreTags.RESOURCE_NAME, "basic.get " + queueName);

    span.setAttribute("amqp.command", "basic.get");
    span.setAttribute("amqp.queue", queue);
  }

  public void onDeliver(final Span span, final String queue, final Envelope envelope) {
    String queueName = queue;
    if (queue == null || queue.isEmpty()) {
      queueName = "<default>";
    } else if (queue.startsWith("amq.gen-")) {
      queueName = "<generated>";
    }
    span.setAttribute(MoreTags.RESOURCE_NAME, "basic.deliver " + queueName);
    span.setAttribute("amqp.command", "basic.deliver");

    if (envelope != null) {
      final String exchange = envelope.getExchange();
      if (exchange != null && !exchange.isEmpty()) {
        span.setAttribute("amqp.exchange", exchange);
      }
      final String routingKey = envelope.getRoutingKey();
      if (routingKey != null && !routingKey.isEmpty()) {
        span.setAttribute("amqp.routing_key", routingKey);
      }
    }
  }

  public void onCommand(final Span span, final Command command) {
    final String name = command.getMethod().protocolMethodName();

    if (!name.equals("basic.publish")) {
      // Don't overwrite the name already set.
      span.setAttribute(MoreTags.RESOURCE_NAME, name);
    }
    span.setAttribute("amqp.command", name);
  }
}
