/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

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
    boolean messagingSpanContextPropagated = false;
    if (spanDecorator instanceof MessagingSpanDecorator) {
      MessagingSpanDecorator messagingSpanDecorator = (MessagingSpanDecorator) spanDecorator;
      messagingSystem = messagingSpanDecorator.getSystem();
      messagingDestination =
          normalizeStableMessagingDestination(
              messagingSystem, messagingSpanDecorator.getDestination(exchange, endpoint));
      messagingDestinationPartitionId = messagingSpanDecorator.getDestinationPartitionId(exchange);
      messagingSpanContextPropagated = messagingSpanDecorator.isSpanContextPropagated();
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
        messagingSpanContextPropagated);
  }

  @Nullable
  private static String normalizeStableMessagingDestination(
      String messagingSystem, @Nullable String messagingDestination) {
    if (!messagingSystem.equals("jms") || messagingDestination == null) {
      return messagingDestination;
    }
    if (messagingDestination.startsWith("queue:")) {
      return messagingDestination.substring("queue:".length());
    }
    if (messagingDestination.startsWith("topic:")) {
      return messagingDestination.substring("topic:".length());
    }
    return messagingDestination;
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

  abstract boolean isMessagingSpanContextPropagated();
}
