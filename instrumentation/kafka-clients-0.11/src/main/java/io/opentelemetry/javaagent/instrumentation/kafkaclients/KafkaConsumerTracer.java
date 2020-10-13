/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients;

import static io.opentelemetry.instrumentation.api.decorator.BaseDecorator.extract;
import static io.opentelemetry.javaagent.instrumentation.kafkaclients.TextMapExtractAdapter.GETTER;
import static io.opentelemetry.trace.Span.Kind.CONSUMER;

import io.grpc.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.record.TimestampType;

public class KafkaConsumerTracer extends BaseTracer {
  public static final KafkaConsumerTracer TRACER = new KafkaConsumerTracer();

  public Span startSpan(ConsumerRecord<?, ?> record) {
    long now = System.currentTimeMillis();

    Span span =
        tracer
            .spanBuilder(spanNameOnConsume(record))
            .setSpanKind(CONSUMER)
            .setParent(extractParent(record))
            .setStartTimestamp(TimeUnit.MILLISECONDS.toNanos(now))
            .setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "kafka")
            .setAttribute(SemanticAttributes.MESSAGING_DESTINATION, record.topic())
            .setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic")
            .setAttribute(SemanticAttributes.MESSAGING_OPERATION, "process")
            .setAttribute(
                SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
                (long) record.serializedValueSize())
            .startSpan();

    onConsume(span, now, record);
    return span;
  }

  private Context extractParent(ConsumerRecord<?, ?> record) {
    if (KafkaClientConfiguration.isPropagationEnabled()) {
      return extract(record.headers(), GETTER);
    } else {
      return Context.current();
    }
  }

  public String spanNameOnConsume(ConsumerRecord<?, ?> record) {
    return record.topic() + " process";
  }

  public void onConsume(Span span, long startTimeMillis, ConsumerRecord<?, ?> record) {
    // TODO should we set topic + offset as messaging.message_id?
    span.setAttribute("partition", record.partition());
    span.setAttribute("offset", record.offset());

    if (record.value() == null) {
      span.setAttribute("tombstone", true);
    }

    // don't record a duration if the message was sent from an old Kafka client
    if (record.timestampType() != TimestampType.NO_TIMESTAMP_TYPE) {
      long produceTime = record.timestamp();
      // this attribute shows how much time elapsed between the producer and the consumer of this
      // message, which can be helpful for identifying queue bottlenecks
      span.setAttribute("record.queue_time_ms", Math.max(0L, startTimeMillis - produceTime));
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.kafka-clients-0.11";
  }
}
