/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.javaagent.instrumentation.camel.v2_20.decorators.MessagingSpanDecorator;
import javax.annotation.Nullable;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

@AutoValue
abstract class CamelRequest {

  static CamelRequest create(
      SpanDecorator spanDecorator,
      Exchange exchange,
      Endpoint endpoint,
      CamelDirection camelDirection,
      SpanKind spanKind) {
    String messagingSystem = null;
    String messagingDestination = null;
    String messagingDestinationPartitionId = null;
    String messagingSendOperationName = null;
    boolean messagingDestinationTemporary = false;
    boolean messagingSpanContextPropagated = false;
    if (spanDecorator instanceof MessagingSpanDecorator) {
      MessagingSpanDecorator messagingSpanDecorator = (MessagingSpanDecorator) spanDecorator;
      messagingSystem = messagingSpanDecorator.getSystem();
      if (emitStableMessagingSemconv()) {
        String stableMessagingDestination =
            messagingSpanDecorator.getStableDestination(exchange, endpoint, camelDirection);
        messagingDestination =
            normalizeStableMessagingDestination(messagingSystem, stableMessagingDestination);
        messagingDestinationTemporary =
            isTemporaryStableMessagingDestination(messagingSystem, stableMessagingDestination);
        messagingDestinationPartitionId =
            messagingSpanDecorator.getDestinationPartitionId(exchange);
      }
      messagingSendOperationName = messagingSpanDecorator.getSendOperationName();
      messagingSpanContextPropagated = messagingSpanDecorator.isSpanContextPropagated(endpoint);
    }
    return new AutoValue_CamelRequest(
        spanDecorator,
        exchange,
        endpoint,
        camelDirection,
        spanKind,
        messagingSystem,
        messagingDestination,
        messagingDestinationPartitionId,
        messagingSendOperationName,
        messagingDestinationTemporary,
        messagingSpanContextPropagated);
  }

  @Nullable
  private static String normalizeStableMessagingDestination(
      String messagingSystem, @Nullable String messagingDestination) {
    // the amqp component is the jms component with an amqp connection factory, so both use the
    // [queue:|topic:|temp-queue:|temp-topic:]destinationName endpoint syntax
    if (messagingDestination == null || !isJmsMessagingSystem(messagingSystem)) {
      return messagingDestination;
    }
    if (messagingDestination.startsWith("queue:")) {
      return messagingDestination.substring("queue:".length());
    }
    if (messagingDestination.startsWith("topic:")) {
      return messagingDestination.substring("topic:".length());
    }
    if (messagingDestination.startsWith("temp-queue:")) {
      return messagingDestination.substring("temp-queue:".length());
    }
    if (messagingDestination.startsWith("temp-topic:")) {
      return messagingDestination.substring("temp-topic:".length());
    }
    return messagingDestination;
  }

  private static boolean isTemporaryStableMessagingDestination(
      String messagingSystem, @Nullable String messagingDestination) {
    return messagingDestination != null
        && isJmsMessagingSystem(messagingSystem)
        && (messagingDestination.startsWith("temp-queue:")
            || messagingDestination.startsWith("temp-topic:"));
  }

  private static boolean isJmsMessagingSystem(String messagingSystem) {
    return messagingSystem.equals("jms") || messagingSystem.equals("amqp");
  }

  abstract SpanDecorator getSpanDecorator();

  abstract Exchange getExchange();

  abstract Endpoint getEndpoint();

  abstract CamelDirection getCamelDirection();

  abstract SpanKind getSpanKind();

  boolean isMessaging() {
    return getMessagingSystem() != null;
  }

  @Nullable
  abstract String getMessagingSystem();

  @Nullable
  abstract String getMessagingDestination();

  @Nullable
  abstract String getMessagingDestinationPartitionId();

  @Nullable
  abstract String getMessagingSendOperationName();

  abstract boolean isMessagingDestinationTemporary();

  abstract boolean isMessagingSpanContextPropagated();
}
