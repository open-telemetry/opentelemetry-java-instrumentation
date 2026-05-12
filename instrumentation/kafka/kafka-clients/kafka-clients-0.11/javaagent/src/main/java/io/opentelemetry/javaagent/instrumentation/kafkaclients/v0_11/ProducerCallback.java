/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11.KafkaSingletons.producerInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProducerRequest;
import javax.annotation.Nullable;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;

public class ProducerCallback implements Callback {
  @Nullable private final Callback callback;
  private final Context parentContext;
  private final Context context;
  private final KafkaProducerRequest request;

  public ProducerCallback(
      @Nullable Callback callback,
      Context parentContext,
      Context context,
      KafkaProducerRequest request) {
    this.callback = callback;
    this.parentContext = parentContext;
    this.context = context;
    this.request = request;
  }

  @Override
  public void onCompletion(@Nullable RecordMetadata metadata, @Nullable Exception exception) {
    producerInstrumenter().end(context, request, metadata, exception);

    if (callback != null) {
      try (Scope ignored = parentContext.makeCurrent()) {
        callback.onCompletion(metadata, exception);
      }
    }
  }
}
