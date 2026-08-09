/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7.RabbitSingletons.CHANNEL_AND_METHOD_CONTEXT_KEY;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Command;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

public class RabbitInstrumenterHelper {
  static final AttributeKey<String> RABBITMQ_COMMAND = AttributeKey.stringKey("rabbitmq.command");

  private static final Set<String> SETTLE_COMMANDS =
      unmodifiableSet(new HashSet<>(asList("basic.ack", "basic.nack", "basic.reject")));

  // spring-cloud-stream's rabbit binder names the queue of a consumer that doesn't declare a group
  // "<destination>.anonymous.<base64url uuid>", using the same generator that spring-amqp uses for
  // the "spring.gen-<base64url uuid>" name of an AnonymousQueue
  private static final Pattern ANONYMOUS_GROUP_QUEUE_NAME =
      Pattern.compile("\\.anonymous\\.[A-Za-z0-9_-]{22}$");

  static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "rabbitmq")
          .getBoolean("experimental_span_attributes/development", false);

  private static final RabbitInstrumenterHelper helper = new RabbitInstrumenterHelper();

  public static RabbitInstrumenterHelper helper() {
    return helper;
  }

  public void onProps(Context context, Span span, AMQP.BasicProperties props) {
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      Integer deliveryMode = props.getDeliveryMode();
      if (deliveryMode != null) {
        span.setAttribute("rabbitmq.delivery_mode", deliveryMode);
      }
    }
    RabbitChannelAndMethodHolder channelContext = context.get(CHANNEL_AND_METHOD_CONTEXT_KEY);
    if (channelContext != null) {
      ChannelAndMethod channelAndMethod = channelContext.getChannelAndMethod();
      if (channelAndMethod != null) {
        channelAndMethod.setHeaders(props.getHeaders());
      }
    }
  }

  static String normalizeExchangeName(@Nullable String exchange) {
    return isDefaultExchange(exchange) ? "<default>" : exchange;
  }

  static boolean isDefaultExchange(@Nullable String exchange) {
    return exchange == null || exchange.isEmpty();
  }

  static boolean isGeneratedQueueName(@Nullable String queue) {
    if (queue == null) {
      return false;
    }
    if (queue.startsWith("amq.gen-") || queue.startsWith("spring.gen-")) {
      return true;
    }
    if (ANONYMOUS_GROUP_QUEUE_NAME.matcher(queue).find()) {
      return true;
    }
    return isCanonicalUuid(queue);
  }

  /**
   * Spring AMQP names anonymous queues with a bare UUID, which would blow up the cardinality of the
   * destination name and of every span name derived from it. The known false positive is a queue
   * that an application deliberately declared under a stable name that happens to be a canonical
   * UUID: it is reported as anonymous and loses its name.
   */
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

  static String producerDestinationName(@Nullable String exchange, @Nullable String routingKey) {
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

  private static void appendDestinationPart(StringBuilder destination, @Nullable String part) {
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

    // the publish and settle spans are named by their span name extractor, from the messaging
    // semantic conventions
    if (!name.equals("basic.publish")
        && !(emitStableMessagingSemconv() && SETTLE_COMMANDS.contains(name))) {
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
