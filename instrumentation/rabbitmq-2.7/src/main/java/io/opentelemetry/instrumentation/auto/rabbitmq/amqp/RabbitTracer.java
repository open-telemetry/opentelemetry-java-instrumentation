/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.rabbitmq.amqp;

import static io.opentelemetry.instrumentation.api.decorator.BaseDecorator.extract;
import static io.opentelemetry.instrumentation.auto.rabbitmq.amqp.TextMapExtractAdapter.GETTER;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static io.opentelemetry.trace.Span.Kind.CONSUMER;
import static io.opentelemetry.trace.Span.Kind.PRODUCER;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RabbitTracer extends BaseTracer {

  public static final RabbitTracer TRACER = new RabbitTracer();

  public Span startSpan(String method, Connection connection) {
    Span.Kind kind = method.equals("Channel.basicPublish") ? PRODUCER : CLIENT;
    Span span = startSpan(method, kind);
    span.setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "rabbitmq");
    span.setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND, "queue");

    NetPeerUtils.setNetPeer(span, connection.getAddress(), connection.getPort());

    return span;
  }

  public Span startGetSpan(
      String queue, long startTime, GetResponse response, Connection connection) {
    Span.Builder spanBuilder =
        tracer
            .spanBuilder(spanNameOnGet(queue))
            .setSpanKind(CLIENT)
            .setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "rabbitmq")
            .setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND, "queue")
            .setAttribute(SemanticAttributes.MESSAGING_OPERATION, "receive")
            .setStartTimestamp(TimeUnit.MILLISECONDS.toNanos(startTime));

    Span span = spanBuilder.startSpan();
    if (response != null) {
      span.setAttribute(
          SemanticAttributes.MESSAGING_DESTINATION,
          normalizeExchangeName(response.getEnvelope().getExchange()));
      span.setAttribute("messaging.rabbitmq.routing_key", response.getEnvelope().getRoutingKey());
      span.setAttribute(
          SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
          (long) response.getBody().length);
    }
    NetPeerUtils.setNetPeer(span, connection.getAddress(), connection.getPort());
    onGet(span, queue);

    return span;
  }

  public Span startDeliverySpan(
      String queue, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
    Map<String, Object> headers = properties.getHeaders();
    long startTimeMillis = System.currentTimeMillis();
    Span span =
        tracer
            .spanBuilder(spanNameOnDeliver(queue))
            .setSpanKind(CONSUMER)
            .setParent(extract(headers, GETTER))
            .setStartTimestamp(TimeUnit.MILLISECONDS.toNanos(startTimeMillis))
            .setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "rabbitmq")
            .setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND, "queue")
            .setAttribute(SemanticAttributes.MESSAGING_OPERATION, "process")
            .startSpan();
    onDeliver(span, envelope);

    if (body != null) {
      span.setAttribute(
          SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, (long) body.length);
    }
    if (properties.getTimestamp() != null) {
      // this will be set if the sender sets the timestamp,
      // or if a plugin is installed on the rabbitmq broker
      long produceTime = properties.getTimestamp().getTime();
      long consumeTime = NANOSECONDS.toMillis(startTimeMillis);
      span.setAttribute("record.queue_time_ms", Math.max(0L, consumeTime - produceTime));
    }

    return span;
  }

  public void onPublish(Span span, String exchange, String routingKey) {
    String exchangeName = normalizeExchangeName(exchange);
    span.setAttribute(SemanticAttributes.MESSAGING_DESTINATION, exchangeName);
    String routing =
        routingKey == null || routingKey.isEmpty()
            ? "<all>"
            : routingKey.startsWith("amq.gen-") ? "<generated>" : routingKey;
    span.updateName(exchangeName + " -> " + routing + " send");
    span.setAttribute("amqp.command", "basic.publish");
    if (routingKey != null && !routingKey.isEmpty()) {
      span.setAttribute("messaging.rabbitmq.routing_key", routingKey);
      span.setAttribute("amqp.routing_key", routingKey);
    }
  }

  public String spanNameOnGet(String queue) {
    return (queue.startsWith("amq.gen-") ? "<generated>" : queue) + " receive";
  }

  public void onGet(Span span, String queue) {
    span.setAttribute("amqp.command", "basic.get");
    span.setAttribute("amqp.queue", queue);
  }

  public String spanNameOnDeliver(String queue) {
    if (queue == null || queue.isEmpty()) {
      return "<default> process";
    } else if (queue.startsWith("amq.gen-")) {
      return "<generated> process";
    } else {
      return queue + " process";
    }
  }

  public void onDeliver(Span span, Envelope envelope) {
    span.setAttribute("amqp.command", "basic.deliver");

    if (envelope != null) {
      String exchange = envelope.getExchange();
      span.setAttribute(SemanticAttributes.MESSAGING_DESTINATION, normalizeExchangeName(exchange));
      String routingKey = envelope.getRoutingKey();
      if (routingKey != null && !routingKey.isEmpty()) {
        span.setAttribute("messaging.rabbitmq.routing_key", routingKey);
      }
    }
  }

  private String normalizeExchangeName(String exchange) {
    return exchange == null || exchange.isEmpty() ? "<default>" : exchange;
  }

  public void onCommand(Span span, Command command) {
    String name = command.getMethod().protocolMethodName();

    if (!name.equals("basic.publish")) {
      span.updateName(name);
    }
    span.setAttribute("amqp.command", name);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.rabbitmq-amqp-2.7";
  }
}
