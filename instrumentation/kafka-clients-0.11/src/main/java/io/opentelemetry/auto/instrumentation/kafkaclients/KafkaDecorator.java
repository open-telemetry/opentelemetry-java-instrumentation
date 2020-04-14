/*
 * Copyright 2020, OpenTelemetry Authors
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
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.ClientDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;

public class KafkaDecorator extends ClientDecorator {
  public static final KafkaDecorator DECORATE = new KafkaDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.kafka-clients-0.11");

  @Override
  protected String service() {
    return "kafka";
  }

  public String spanNameOnConsume(final ConsumerRecord record) {
    final String topic = record.topic();
    if (topic != null) {
      return topic;
    } else {
      return "destination";
    }
  }

  public String spanNameOnProduce(final ProducerRecord record) {
    if (record != null) {
      final String topic = record.topic();
      if (topic != null) {
        return topic;
      }
    }
    return "destination";
  }

  public void onConsume(final Span span, final ConsumerRecord record) {
    span.setAttribute("partition", record.partition());
    span.setAttribute("offset", record.offset());
  }

  public void onProduce(final Span span, final ProducerRecord record) {
    if (record != null) {
      final Integer partition = record.partition();
      if (partition != null) {
        span.setAttribute("kafka.partition", partition);
      }
    }
  }
}
