/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.UrlParser.parseUrl;

import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.ProducerData;
import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.UrlParser.UrlData;
import java.lang.reflect.Method;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;

public class PulsarRequest extends BasePulsarRequest {
  private static final ClassValue<Optional<Method>> INNER_MESSAGE_ID_METHOD =
      new ClassValue<Optional<Method>>() {
        @Override
        protected Optional<Method> computeValue(Class<?> type) {
          return findMethod(type, "getInnerMessageId");
        }
      };

  private static final ClassValue<Optional<Method>> INNER_MESSAGE_METHOD =
      new ClassValue<Optional<Method>>() {
        @Override
        protected Optional<Method> computeValue(Class<?> type) {
          return findMethod(type, "getMessage");
        }
      };

  private final Message<?> message;
  private final String messageId;

  public static PulsarRequest create(Message<?> message) {
    return new PulsarRequest(message, message.getTopicName(), null);
  }

  public static PulsarRequest create(Message<?> message, @Nullable String url) {
    return new PulsarRequest(message, message.getTopicName(), parseUrl(url));
  }

  public static PulsarRequest create(Message<?> message, @Nullable UrlData urlData) {
    return new PulsarRequest(message, message.getTopicName(), urlData);
  }

  public static PulsarRequest create(Message<?> message, ProducerData producerData) {
    return new PulsarRequest(message, producerData.topic, parseUrl(producerData.url));
  }

  private PulsarRequest(Message<?> message, String destination, @Nullable UrlData urlData) {
    super(destination, urlData);
    this.message = message;
    // for producer spans message id is not available when the PulsarRequest is created, so we will
    // try to get it later when it's available
    MessageId id = message.getMessageId();
    this.messageId = id != null ? id.toString() : null;
  }

  public Message<?> getMessage() {
    return message;
  }

  @Nullable
  public String getMessageId() {
    if (messageId != null) {
      return messageId;
    }
    return extractMessageId(message);
  }

  @Nullable
  private static String extractMessageId(Message<?> message) {
    MessageId id = message.getMessageId();
    if (id != null) {
      return id.toString();
    }
    id = invokeMessageIdMethod(message, "getInnerMessageId");
    if (id != null) {
      return id.toString();
    }
    Message<?> innerMessage = invokeMessageMethod(message, "getMessage");
    if (innerMessage != null) {
      MessageId innerId = innerMessage.getMessageId();
      if (innerId != null) {
        return innerId.toString();
      }
    }
    return null;
  }

  @Nullable
  private static MessageId invokeMessageIdMethod(Message<?> message, String methodName) {
    try {
      Optional<Method> method =
          "getInnerMessageId".equals(methodName)
              ? INNER_MESSAGE_ID_METHOD.get(message.getClass())
              : findMethod(message.getClass(), methodName);
      Method cachedMethod = method.orElse(null);
      if (cachedMethod == null) {
        return null;
      }
      Object result = cachedMethod.invoke(message);
      return result instanceof MessageId ? (MessageId) result : null;
    } catch (Throwable ignored) {
      return null;
    }
  }

  // Reflection can only tell us this is some Message<?> implementation; erasing to Message<?> is
  // safe because we only call getMessageId() on the returned value.
  @SuppressWarnings("unchecked")
  @Nullable
  private static Message<?> invokeMessageMethod(Message<?> message, String methodName) {
    try {
      Optional<Method> method =
          "getMessage".equals(methodName)
              ? INNER_MESSAGE_METHOD.get(message.getClass())
              : findMethod(message.getClass(), methodName);
      Method cachedMethod = method.orElse(null);
      if (cachedMethod == null) {
        return null;
      }
      Object result = cachedMethod.invoke(message);
      return result instanceof Message ? (Message<?>) result : null;
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static Optional<Method> findMethod(Class<?> type, String methodName) {
    try {
      return Optional.of(type.getMethod(methodName));
    } catch (Throwable ignored) {
      return Optional.empty();
    }
  }
}
