/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients;

import static io.opentelemetry.javaagent.instrumentation.kafkaclients.KafkaProducerTracer.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;

public class ProducerCallback implements Callback {
  private final Callback callback;
  private final Context parent;
  private final Span span;

  public ProducerCallback(Callback callback, Context parent, Span span) {
    this.callback = callback;
    this.parent = parent;
    this.span = span;
  }

  @Override
  public void onCompletion(RecordMetadata metadata, Exception exception) {
    if (exception != null) {
      tracer().endExceptionally(span, exception);
    } else {
      tracer().end(span);
    }

    if (callback != null) {
      if (parent != null) {
        try (Scope ignored = parent.makeCurrent()) {
          callback.onCompletion(metadata, exception);
        }
      } else {
        callback.onCompletion(metadata, exception);
      }
    }
  }
}
