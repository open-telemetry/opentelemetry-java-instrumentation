/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7.RabbitSingletons.CHANNEL_AND_METHOD_CONTEXT_KEY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_ANONYMOUS;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Command;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import java.util.Map;
import javax.annotation.Nullable;

public class RabbitInstrumenterHelper {
  static final AttributeKey<String> RABBITMQ_COMMAND = AttributeKey.stringKey("rabbitmq.command");

  static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "rabbitmq")
          .getBoolean("experimental_span_attributes/development", false);

  private static final RabbitInstrumenterHelper helper = new RabbitInstrumenterHelper();

  public static RabbitInstrumenterHelper helper() {
    return helper;
  }

  public void onPublish(Span span, String exchange, String routingKey) {
    String exchangeName = normalizeExchangeName(exchange);
    if (emitStableMessagingSemconv()) {
      String destinationName = producerDestinationName(exchange, routingKey);
      span.setAttribute(MESSAGING_DESTINATION_NAME, destinationName);
      boolean anonymousDestination =
          isDefaultExchange(exchange) && isGeneratedQueueName(routingKey);
      if (anonymousDestination) {
        span.setAttribute(MESSAGING_DESTINATION_ANONYMOUS, true);
      }
      span.updateName(anonymousDestination ? "publish" : "publish " + destinationName);
    } else {
      span.setAttribute(MESSAGING_DESTINATION_NAME, exchangeName);
      span.updateName(exchangeName + " publish");
    }
    if (routingKey != null && !routingKey.isEmpty()) {
      span.setAttribute(MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY, routingKey);
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
    return isDefaultExchange(exchange) ? "<default>" : exchange;
  }

  private static boolean isDefaultExchange(@Nullable String exchange) {
    return exchange == null || exchange.isEmpty();
  }

  static boolean isGeneratedQueueName(@Nullable String queue) {
    if (queue == null) {
      return false;
    }
    if (queue.startsWith("amq.gen-") || queue.startsWith("spring.gen-")) {
      return true;
    }
    return isCanonicalUuid(queue);
  }

  private static boolean isCanonicalUuid(String value) {
    if (value.length() != 36) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (i == 8 || i == 13 || i == 18 || i == 23) {
        if (ch != '-') {
          return false;
        }
      } else if (!((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f'))) {
        return false;
      }
    }
    return true;
  }

  static String producerDestinationName(String exchange, String routingKey) {
    StringBuilder destination = new StringBuilder();
    appendDestinationPart(destination, exchange);
    appendDestinationPart(destination, routingKey);
    return destination.length() == 0 ? "amq.default" : destination.toString();
  }

  @Nullable
  static String consumerDestinationName(String exchange, String routingKey, String queue) {
    StringBuilder destination = new StringBuilder();
    appendDestinationPart(destination, exchange);
    appendDestinationPart(destination, routingKey);
    if (queue != null && !queue.equals(routingKey)) {
      appendDestinationPart(destination, queue);
    }
    return destination.length() == 0 ? null : destination.toString();
  }

  private static void appendDestinationPart(StringBuilder destination, String part) {
    if (part == null || part.isEmpty()) {
      return;
    }
    if (destination.length() != 0) {
      destination.append(':');
    }
    destination.append(part);
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
