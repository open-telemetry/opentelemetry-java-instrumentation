/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class SpringRabbitMessageAttributesGetter implements MessagingAttributesGetter<Message, Void> {

  @Nullable private static final Method getConsumerQueue = getConsumerQueueMethod();

  @Override
  public String getSystem(Message message) {
    return "rabbitmq";
  }

  @Override
  @Nullable
  public String getDestination(Message message) {
    MessageProperties properties = message.getMessageProperties();
    if (!emitStableMessagingSemconv()) {
      return properties.getReceivedRoutingKey();
    }

    String exchange = properties.getReceivedExchange();
    String routingKey = properties.getReceivedRoutingKey();
    String queue = getConsumerQueue(properties);
    StringBuilder destination = new StringBuilder();
    appendDestinationPart(destination, exchange);
    appendDestinationPart(destination, routingKey);
    if (queue != null && !queue.equals(routingKey)) {
      appendDestinationPart(destination, queue);
    }
    return destination.length() == 0 ? null : destination.toString();
  }

  @Nullable
  private static Method getConsumerQueueMethod() {
    try {
      return MessageProperties.class.getMethod("getConsumerQueue");
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }

  @Nullable
  private static String getConsumerQueue(MessageProperties properties) {
    if (getConsumerQueue == null) {
      return null;
    }
    try {
      return (String) getConsumerQueue.invoke(properties);
    } catch (IllegalAccessException | InvocationTargetException ignored) {
      return null;
    }
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

  @Nullable
  @Override
  public String getDestinationTemplate(Message message) {
    return null;
  }

  @Override
  public boolean isTemporaryDestination(Message message) {
    return false;
  }

  @Override
  public boolean isAnonymousDestination(Message message) {
    return isGeneratedQueueName(getConsumerQueue(message.getMessageProperties()));
  }

  private static boolean isGeneratedQueueName(@Nullable String queue) {
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

  @Override
  @Nullable
  public String getConversationId(Message message) {
    return null;
  }

  @Override
  public Long getMessageBodySize(Message message) {
    return message.getMessageProperties().getContentLength();
  }

  @Nullable
  @Override
  public Long getMessageEnvelopeSize(Message message) {
    return null;
  }

  @Override
  @Nullable
  public String getMessageId(Message message, @Nullable Void unused) {
    return message.getMessageProperties().getMessageId();
  }

  @Nullable
  @Override
  public String getClientId(Message message) {
    return null;
  }

  @Nullable
  @Override
  public Long getBatchMessageCount(Message message, @Nullable Void unused) {
    return null;
  }

  @Override
  public List<String> getMessageHeader(Message message, String name) {
    Object value = message.getMessageProperties().getHeaders().get(name);
    if (value != null) {
      return singletonList(value.toString());
    }
    return emptyList();
  }
}
