/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import static io.opentelemetry.javaagent.instrumentation.kafkastreams.TextMapExtractAdapter.GETTER;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Span.Kind;
import io.opentelemetry.api.trace.attributes.SemanticAttributes;
import io.opentelemetry.instrumentation.api.instrumenter.BaseInstrumenter;
import org.apache.kafka.streams.processor.internals.StampedRecord;

public class KafkaStreamsTracer extends BaseInstrumenter {
  private static final KafkaStreamsTracer TRACER = new KafkaStreamsTracer();

  public static KafkaStreamsTracer tracer() {
    return TRACER;
  }

  public Span startSpan(StampedRecord record) {
    Span span =
        tracer
            .spanBuilder(spanNameForConsume(record))
            .setSpanKind(Kind.CONSUMER)
            .setParent(extract(record.value.headers(), GETTER))
            .setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "kafka")
            .setAttribute(SemanticAttributes.MESSAGING_DESTINATION, record.topic())
            .setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic")
            .setAttribute(SemanticAttributes.MESSAGING_OPERATION, "process")
            .startSpan();
    onConsume(span, record);
    return span;
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
      span.setAttribute("kafka-streams.offset", record.offset());
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.kafka-streams";
  }
}
