/*
 * Copyright The OpenTelemetry Authors
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

package io.opentelemetry.instrumentation.auto.rabbitmq.amqp;

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
