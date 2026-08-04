/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

/**
 * Emits {@code messaging.destination.subscription.name}, which only exists in the new messaging
 * semantic conventions.
 */
final class PulsarSubscriptionAttributesExtractor<T extends BasePulsarRequest>
    implements AttributesExtractor<T, Void> {

  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<String> MESSAGING_DESTINATION_SUBSCRIPTION_NAME =
      AttributeKey.stringKey("messaging.destination.subscription.name");

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, T request) {
    attributes.put(MESSAGING_DESTINATION_SUBSCRIPTION_NAME, request.getSubscription());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      T request,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
