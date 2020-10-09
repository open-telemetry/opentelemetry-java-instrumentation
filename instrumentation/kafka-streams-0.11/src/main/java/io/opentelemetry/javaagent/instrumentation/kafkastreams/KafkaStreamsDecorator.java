/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.instrumentation.api.decorator.ClientDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import org.apache.kafka.streams.processor.internals.StampedRecord;

public class KafkaStreamsDecorator extends ClientDecorator {
  public static final KafkaStreamsDecorator CONSUMER_DECORATE = new KafkaStreamsDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracer("io.opentelemetry.auto.kafka-streams-0.11");

  public String spanNameForConsume(StampedRecord record) {
    if (record == null) {
      return null;
    }
    String topic = record.topic();
    if (topic != null) {
      return topic;
    } else {
      return "destination";
    }
  }

  public void onConsume(Span span, StampedRecord record) {
    if (record != null) {
      span.setAttribute("partition", record.partition());
      span.setAttribute("offset", record.offset());
    }
  }
}
