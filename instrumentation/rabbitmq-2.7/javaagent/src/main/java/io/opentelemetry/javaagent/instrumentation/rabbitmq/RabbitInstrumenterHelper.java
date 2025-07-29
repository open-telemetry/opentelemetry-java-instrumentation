/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import static io.opentelemetry.javaagent.instrumentation.rabbitmq.RabbitSingletons.CHANNEL_AND_METHOD_CONTEXT_KEY;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Command;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import java.util.Map;

public class RabbitInstrumenterHelper {
  static final AttributeKey<String> RABBITMQ_COMMAND = AttributeKey.stringKey("rabbitmq.command");

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      AgentInstrumentationConfig.get()
          .getBoolean("otel.instrumentation.rabbitmq.experimental-span-attributes", false);

  private static final RabbitInstrumenterHelper INSTRUMENTER_HELPER =
      new RabbitInstrumenterHelper();

  public static RabbitInstrumenterHelper helper() {
    return INSTRUMENTER_HELPER;
  }

  public void onPublish(Span span, String exchange, String routingKey) {
    String exchangeName = normalizeExchangeName(exchange);
    span.setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME, exchangeName);
    span.updateName(exchangeName + " publish");
    if (routingKey != null && !routingKey.isEmpty()) {
      span.setAttribute(
          MessagingIncubatingAttributes.MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY, routingKey);
    }
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      span.setAttribute(RABBITMQ_COMMAND, "basic.publish");
    }
  }

  public void onProps(Context context, Span span, AMQP.BasicProperties props) {
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      Integer deliveryMode = props.getDeliveryMode();
      if (deliveryMode != null) {
        span.setAttribute("rabbitmq.delivery_mode", deliveryMode);
      }
    }
    RabbitChannelAndMethodHolder channelContext = context.get(CHANNEL_AND_METHOD_CONTEXT_KEY);
    ChannelAndMethod channelAndMethod = channelContext.getChannelAndMethod();
    if (channelAndMethod != null) {
      channelAndMethod.setHeaders(props.getHeaders());
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
      span.setAttribute(RABBITMQ_COMMAND, name);
    }
  }

  public void inject(Context context, Map<String, Object> headers, MapSetter setter) {
    GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(context, headers, setter);
  }

  public void setChannelAndMethod(Context context, ChannelAndMethod channelAndMethod) {
    RabbitChannelAndMethodHolder holder = context.get(CHANNEL_AND_METHOD_CONTEXT_KEY);
    if (holder != null) {
      holder.setChannelAndMethod(channelAndMethod);
    }
  }
}
