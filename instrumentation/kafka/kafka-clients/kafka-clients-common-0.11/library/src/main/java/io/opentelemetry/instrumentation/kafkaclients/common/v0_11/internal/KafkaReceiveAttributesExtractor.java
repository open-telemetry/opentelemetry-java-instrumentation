/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

final class KafkaReceiveAttributesExtractor
    implements AttributesExtractor<KafkaReceiveRequest, Void> {

  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<String> MESSAGING_KAFKA_CONSUMER_GROUP =
      AttributeKey.stringKey("messaging.kafka.consumer.group");
  private static final AttributeKey<String> MESSAGING_CONSUMER_GROUP_NAME =
      AttributeKey.stringKey("messaging.consumer.group.name");

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, KafkaReceiveRequest request) {
    if (emitOldMessagingSemconv()) {
      attributes.put(MESSAGING_KAFKA_CONSUMER_GROUP, request.getConsumerGroup());
    }
    if (emitStableMessagingSemconv()) {
      attributes.put(MESSAGING_CONSUMER_GROUP_NAME, request.getConsumerGroup());
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      KafkaReceiveRequest request,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
