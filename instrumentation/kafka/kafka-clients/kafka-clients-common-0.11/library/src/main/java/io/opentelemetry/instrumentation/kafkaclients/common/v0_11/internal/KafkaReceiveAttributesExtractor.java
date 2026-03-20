/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

class KafkaReceiveAttributesExtractor implements AttributesExtractor<KafkaReceiveRequest, Void> {

  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<String> MESSAGING_KAFKA_CONSUMER_GROUP =
      AttributeKey.stringKey("messaging.kafka.consumer.group");

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, KafkaReceiveRequest request) {

    attributes.put(MESSAGING_KAFKA_CONSUMER_GROUP, request.getConsumerGroup());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      KafkaReceiveRequest request,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
