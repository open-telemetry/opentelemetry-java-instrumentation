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

enum KafkaReceiveAttributesExtractor implements AttributesExtractor<KafkaReceiveRequest, Void> {
  INSTANCE;

  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<String> MESSAGING_KAFKA_CONSUMER_GROUP =
      AttributeKey.stringKey("messaging.kafka.consumer.group");
  private static final AttributeKey<String> MESSAGING_KAFKA_BOOTSTRAP_SERVERS =
      AttributeKey.stringKey("messaging.kafka.bootstrap.servers");

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, KafkaReceiveRequest request) {

    String consumerGroup = request.getConsumerGroup();
    if (consumerGroup != null) {
      attributes.put(MESSAGING_KAFKA_CONSUMER_GROUP, consumerGroup);
    }

    String bootstrapServers = request.getBootstrapServers();
    if (bootstrapServers != null) {
      attributes.put(MESSAGING_KAFKA_BOOTSTRAP_SERVERS, bootstrapServers);
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
