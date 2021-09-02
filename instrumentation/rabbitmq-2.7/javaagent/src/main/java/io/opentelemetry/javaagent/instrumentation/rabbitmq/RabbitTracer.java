/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.javaagent.instrumentation.rabbitmq.TextMapExtractAdapter.GETTER;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.api.tracer.net.NetPeerAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RabbitTracer extends BaseTracer {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get().getBoolean("otel.instrumentation.rabbitmq.experimental-span-attributes", false);

  private static final RabbitTracer TRACER = new RabbitTracer();

  public static RabbitTracer tracer() {
    return TRACER;
  }

  public Context startSpan(String method, Connection connection) {
    Context parentContext = Context.current();
    SpanKind kind = method.equals("Channel.basicPublish") ? PRODUCER : CLIENT;
    SpanBuilder span =
        spanBuilder(parentContext, method, kind)
            .setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "rabbitmq")
            .setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND, "queue");

    NetPeerAttributes.INSTANCE.setNetPeer(span, connection.getAddress(), connection.getPort());

    return parentContext.with(span.startSpan());
  }

  public Context startGetSpan(
      String queue, long startTime, GetResponse response, Connection connection) {
    Context parentContext = Context.current();
    SpanBuilder spanBuilder =
        spanBuilder(parentContext, spanNameOnGet(queue), CLIENT)
            .setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "rabbitmq")
            .setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND, "queue")
            .setAttribute(SemanticAttributes.MESSAGING_OPERATION, "receive")
            .setStartTimestamp(startTime, TimeUnit.MILLISECONDS);

    if (response != null) {
      spanBuilder.setAttribute(
          SemanticAttributes.MESSAGING_DESTINATION,
          normalizeExchangeName(response.getEnvelope().getExchange()));
      spanBuilder.setAttribute(
          SemanticAttributes.MESSAGING_RABBITMQ_ROUTING_KEY,
          response.getEnvelope().getRoutingKey());
      spanBuilder.setAttribute(
          SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
          (long) response.getBody().length);
    }
    NetPeerAttributes.INSTANCE.setNetPeer(
        spanBuilder, connection.getAddress(), connection.getPort());
    onGet(spanBuilder, queue);

    // TODO: withClientSpan()?
    return parentContext.with(spanBuilder.startSpan());
  }

  public Context startDeliverySpan(
      String queue, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
    Map<String, Object> headers = properties.getHeaders();
    Context parentContext = extract(headers, GETTER);

    long startTimeMillis = System.currentTimeMillis();
    Span span =
        spanBuilder(parentContext, spanNameOnDeliver(queue), CONSUMER)
            .setStartTimestamp(startTimeMillis, TimeUnit.MILLISECONDS)
            .setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "rabbitmq")
            .setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND, "queue")
            .setAttribute(SemanticAttributes.MESSAGING_OPERATION, "process")
            .startSpan();
    onDeliver(span, envelope);

    if (body != null) {
      span.setAttribute(
          SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, (long) body.length);
    }
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES && properties.getTimestamp() != null) {
      // this will be set if the sender sets the timestamp,
      // or if a plugin is installed on the rabbitmq broker
      long produceTimeMillis = properties.getTimestamp().getTime();
      span.setAttribute(
          "rabbitmq.record.queue_time_ms", Math.max(0L, startTimeMillis - produceTimeMillis));
    }

    return withConsumerSpan(parentContext, span);
  }

  public void onPublish(Span span, String exchange, String routingKey) {
    String exchangeName = normalizeExchangeName(exchange);
    span.setAttribute(SemanticAttributes.MESSAGING_DESTINATION, exchangeName);
    span.updateName(exchangeName + " send");
    if (routingKey != null && !routingKey.isEmpty()) {
      span.setAttribute(SemanticAttributes.MESSAGING_RABBITMQ_ROUTING_KEY, routingKey);
    }
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      span.setAttribute("rabbitmq.command", "basic.publish");
    }
  }

  public void onProps(Span span, AMQP.BasicProperties props) {
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      Integer deliveryMode = props.getDeliveryMode();
      if (deliveryMode != null) {
        span.setAttribute("rabbitmq.delivery_mode", deliveryMode);
      }
    }
  }

  public String spanNameOnGet(String queue) {
    return (queue.startsWith("amq.gen-") ? "<generated>" : queue) + " receive";
  }

  public void onGet(SpanBuilder span, String queue) {
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      span.setAttribute("rabbitmq.command", "basic.get");
      span.setAttribute("rabbitmq.queue", queue);
    }
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
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      span.setAttribute("rabbitmq.command", "basic.deliver");
    }

    if (envelope != null) {
      String exchange = envelope.getExchange();
      span.setAttribute(SemanticAttributes.MESSAGING_DESTINATION, normalizeExchangeName(exchange));
      String routingKey = envelope.getRoutingKey();
      if (routingKey != null && !routingKey.isEmpty()) {
        span.setAttribute(SemanticAttributes.MESSAGING_RABBITMQ_ROUTING_KEY, routingKey);
      }
    }
  }

  private static String normalizeExchangeName(String exchange) {
    return exchange == null || exchange.isEmpty() ? "<default>" : exchange;
  }

  public static void onCommand(Span span, Command command) {
    String name = command.getMethod().protocolMethodName();

    if (!name.equals("basic.publish")) {
      span.updateName(name);
    }
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      span.setAttribute("rabbitmq.command", name);
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.rabbitmq-2.7";
  }
}
