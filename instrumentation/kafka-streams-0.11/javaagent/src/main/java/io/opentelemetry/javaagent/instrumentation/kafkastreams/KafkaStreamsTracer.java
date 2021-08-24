/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.javaagent.instrumentation.kafkastreams.TextMapExtractAdapter.GETTER;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.kafka.streams.processor.internals.StampedRecord;

public class KafkaStreamsTracer extends BaseTracer {
  private static final KafkaStreamsTracer TRACER = new KafkaStreamsTracer();

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get().getBoolean("otel.instrumentation.kafka.experimental-span-attributes", false);

  public static KafkaStreamsTracer tracer() {
    return TRACER;
  }

  public Context startSpan(StampedRecord record) {
    Context parentContext = extract(record.value.headers(), GETTER);
    Span span =
        spanBuilder(parentContext, spanNameForConsume(record), CONSUMER)
            .setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "kafka")
            .setAttribute(SemanticAttributes.MESSAGING_DESTINATION, record.topic())
            .setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic")
            .setAttribute(SemanticAttributes.MESSAGING_OPERATION, "process")
            .startSpan();
    onConsume(span, record);
    return withConsumerSpan(parentContext, span);
  }

  public String spanNameForConsume(StampedRecord record) {
    if (record == null) {
      return null;
    }
    return record.topic() + " process";
  }

  public void onConsume(Span span, StampedRecord record) {
    if (record != null) {
      span.setAttribute(SemanticAttributes.MESSAGING_KAFKA_PARTITION, record.partition());
      if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
        span.setAttribute("kafka.offset", record.offset());
      }
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.kafka-streams-0.11";
  }
}
