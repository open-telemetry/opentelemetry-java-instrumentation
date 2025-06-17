/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.List;
import javax.annotation.Nullable;

class KafkaExperimentalAttributesExtractor<REQUEST extends AbstractKafkaRequest, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  private static final AttributeKey<List<String>> MESSAGING_KAFKA_BOOTSTRAP_SERVERS =
      AttributeKey.stringArrayKey("messaging.kafka.bootstrap.servers");

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {

    List<String> bootstrapServers = request.getBootstrapServers();
    attributes.put(MESSAGING_KAFKA_BOOTSTRAP_SERVERS, bootstrapServers);
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {}
}
