/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.kafkaclients;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.instrumentation.library.api.decorator.ClientDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.record.TimestampType;

public class KafkaDecorator extends ClientDecorator {
  public static final KafkaDecorator DECORATE = new KafkaDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.kafka-clients-0.11");

  public String spanNameOnConsume(final ConsumerRecord record) {
    String topic = record.topic();
    if (topic != null) {
      return topic;
    } else {
      return "destination";
    }
  }

  public String spanNameOnProduce(final ProducerRecord record) {
    if (record != null) {
      String topic = record.topic();
      if (topic != null) {
        return topic;
      }
    }
    return "destination";
  }

  public void onConsume(final Span span, final long startTimeMillis, final ConsumerRecord record) {
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

  public void onProduce(final Span span, final ProducerRecord record) {
    if (record != null) {
      Integer partition = record.partition();
      if (partition != null) {
        span.setAttribute("partition", partition);
      }
    }
  }
}
