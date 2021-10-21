/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Command;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Map;

public class RabbitInstrumenterHelper {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get().getBoolean("otel.instrumentation.rabbitmq.experimental-span-attributes", false);

  private static final RabbitInstrumenterHelper INSTRUMENTER_HELPER =
      new RabbitInstrumenterHelper();

  public static RabbitInstrumenterHelper helper() {
    return INSTRUMENTER_HELPER;
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

  public void inject(Context context, Map<String, Object> headers, TextMapInjectAdapter setter) {
    GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(context, headers, setter);
  }
}
