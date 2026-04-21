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
import org.apache.kafka.clients.producer.RecordMetadata;

final class KafkaProducerExperimentalAttributesExtractor
    implements AttributesExtractor<KafkaProducerRequest, RecordMetadata> {

  private static final AttributeKey<String> MESSAGING_KAFKA_BOOTSTRAP_SERVERS =
      AttributeKey.stringKey("messaging.kafka.bootstrap.servers");

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, KafkaProducerRequest request) {
    attributes.put(MESSAGING_KAFKA_BOOTSTRAP_SERVERS, request.getBootstrapServers());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      KafkaProducerRequest request,
      @Nullable RecordMetadata recordMetadata,
      @Nullable Throwable error) {}
}
