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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.springframework.amqp.core.MessageProperties;

class SpringRabbitMessageAttributesGetter
    implements MessagingAttributesGetter<SpringRabbitRequest, Void> {

  @Nullable private static final Method getConsumerQueue = getConsumerQueueMethod();

  // spring-cloud-stream's rabbit binder names the queue of a consumer that doesn't declare a group
  // "<destination>.anonymous.<base64url uuid>", using the same generator that spring-amqp uses for
  // the "spring.gen-<base64url uuid>" name of an AnonymousQueue
  private static final Pattern ANONYMOUS_GROUP_QUEUE_NAME =
      Pattern.compile("\\.anonymous\\.[A-Za-z0-9_-]{22}$");

  @Override
  public String getSystem(SpringRabbitRequest request) {
    return "rabbitmq";
  }

  @Override
  @Nullable
  public String getDestination(SpringRabbitRequest request) {
    MessageProperties properties = request.getMessage().getMessageProperties();
    if (!emitStableMessagingSemconv()) {
      return properties.getReceivedRoutingKey();
    }

    String exchange = properties.getReceivedExchange();
    String routingKey = properties.getReceivedRoutingKey();
    String queue = getQueue(properties);
    StringBuilder destination = new StringBuilder();
    appendDestinationPart(destination, exchange);
    appendDestinationPart(destination, routingKey);
    if (queue != null && !queue.equals(routingKey)) {
      appendDestinationPart(destination, queue);
    }
    return destination.length() == 0 ? null : destination.toString();
  }

  /**
   * Returns the name of the queue that the message was consumed from, or {@code null} when it can't
   * be determined.
   */
  @Nullable
  private static String getQueue(MessageProperties properties) {
    String queue = getConsumerQueue(properties);
    if (queue != null) {
      return queue;
    }
    // getConsumerQueue() was only added in spring-amqp 1.4, and even where it exists it is only
    // populated for a message that a listener container consumed; every queue is bound to the
    // default exchange under its own name, so a message received from it names its queue in the
    // routing key
    String exchange = properties.getReceivedExchange();
    return exchange == null || exchange.isEmpty() ? properties.getReceivedRoutingKey() : null;
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
  public String getDestinationTemplate(SpringRabbitRequest request) {
    return null;
  }

  @Override
  public boolean isTemporaryDestination(SpringRabbitRequest request) {
    return false;
  }

  @Override
  public boolean isAnonymousDestination(SpringRabbitRequest request) {
    return emitStableMessagingSemconv()
        && isGeneratedQueueName(getQueue(request.getMessage().getMessageProperties()));
  }

  private static boolean isGeneratedQueueName(@Nullable String queue) {
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

  @Override
  @Nullable
  public String getConversationId(SpringRabbitRequest request) {
    return null;
  }

  @Override
  @Nullable
  public Long getMessageBodySize(SpringRabbitRequest request) {
    if (request.isBatch()) {
      return null;
    }
    byte[] body = request.getMessage().getBody();
    return body == null ? null : (long) body.length;
  }

  @Nullable
  @Override
  public Long getMessageEnvelopeSize(SpringRabbitRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getMessageId(SpringRabbitRequest request, @Nullable Void unused) {
    return request.getMessage().getMessageProperties().getMessageId();
  }

  @Nullable
  @Override
  public String getClientId(SpringRabbitRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getBatchMessageCount(SpringRabbitRequest request, @Nullable Void unused) {
    return request.isBatch() ? (long) request.getBatchMessageCount() : null;
  }

  @Override
  public List<String> getMessageHeader(SpringRabbitRequest request, String name) {
    Object value = request.getMessage().getMessageProperties().getHeaders().get(name);
    if (value != null) {
      return singletonList(value.toString());
    }
    return emptyList();
  }

  @Override
  public Collection<String> getMessageHeaderNames(SpringRabbitRequest request) {
    return new ArrayList<>(request.getMessage().getMessageProperties().getHeaders().keySet());
  }
}
