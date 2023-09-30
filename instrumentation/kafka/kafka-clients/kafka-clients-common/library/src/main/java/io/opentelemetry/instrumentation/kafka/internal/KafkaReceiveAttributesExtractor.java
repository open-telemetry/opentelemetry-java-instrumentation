/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.SemanticAttributes;
import javax.annotation.Nullable;

enum KafkaReceiveAttributesExtractor implements AttributesExtractor<KafkaReceiveRequest, Void> {
  INSTANCE;

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, KafkaReceiveRequest request) {

    String consumerGroup = request.getConsumerGroup();
    if (consumerGroup != null) {
      attributes.put(SemanticAttributes.MESSAGING_KAFKA_CONSUMER_GROUP, consumerGroup);
    }

    String clientId = request.getClientId();
    if (clientId != null) {
      attributes.put(SemanticAttributes.MESSAGING_CLIENT_ID, clientId);
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
