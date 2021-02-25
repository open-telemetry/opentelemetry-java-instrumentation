/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients;

import static io.opentelemetry.javaagent.instrumentation.kafkaclients.KafkaProducerTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;

public class ProducerCallback implements Callback {
  private final Callback callback;
  private final Context parentContext;
  private final Context context;

  public ProducerCallback(Callback callback, Context parentContext, Context context) {
    this.callback = callback;
    this.parentContext = parentContext;
    this.context = context;
  }

  @Override
  public void onCompletion(RecordMetadata metadata, Exception exception) {
    if (exception != null) {
      tracer().endExceptionally(context, exception);
    } else {
      tracer().end(context);
    }

    if (callback != null) {
      try (Scope ignored = parentContext.makeCurrent()) {
        callback.onCompletion(metadata, exception);
      }
    }
  }
}
