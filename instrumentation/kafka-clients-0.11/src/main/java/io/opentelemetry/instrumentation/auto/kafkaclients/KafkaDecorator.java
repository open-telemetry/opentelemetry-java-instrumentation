/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.kafkaclients;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.instrumentation.api.decorator.ClientDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.record.TimestampType;

public class KafkaDecorator extends ClientDecorator {
  public static final KafkaDecorator DECORATE = new KafkaDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracer("io.opentelemetry.auto.kafka-clients-0.11");

  public String spanNameOnConsume(ConsumerRecord record) {
    String topic = record.topic();
    if (topic != null) {
      return topic;
    } else {
      return "destination";
    }
  }

  public String spanNameOnProduce(ProducerRecord record) {
    if (record != null) {
      String topic = record.topic();
      if (topic != null) {
        return topic;
      }
    }
    return "destination";
  }

  public void onConsume(Span span, long startTimeMillis, ConsumerRecord record) {
    span.setAttribute("partition", record.partition());
    span.setAttribute("offset", record.offset());
    // don't record a duration if the message was sent from an old Kafka client
    if (record.timestampType() != TimestampType.NO_TIMESTAMP_TYPE) {
      long produceTime = record.timestamp();
      // this attribute shows how much time elapsed between the producer and the consumer of this
      // message, which can be helpful for identifying queue bottlenecks
      span.setAttribute("record.queue_time_ms", Math.max(0L, startTimeMillis - produceTime));
    }
  }

  public void onProduce(Span span, ProducerRecord record) {
    if (record != null) {
      Integer partition = record.partition();
      if (partition != null) {
        span.setAttribute("partition", partition);
      }
    }
  }
}
