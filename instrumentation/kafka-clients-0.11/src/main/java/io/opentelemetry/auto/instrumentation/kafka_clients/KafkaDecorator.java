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
package io.opentelemetry.auto.instrumentation.kafka_clients;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.ClientDecorator;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.SpanTypes;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;

public abstract class KafkaDecorator extends ClientDecorator {
  public static final KafkaDecorator PRODUCER_DECORATE =
      new KafkaDecorator() {
        @Override
        protected String getSpanType() {
          return SpanTypes.MESSAGE_PRODUCER;
        }
      };

  public static final KafkaDecorator CONSUMER_DECORATE =
      new KafkaDecorator() {
        @Override
        protected String getSpanType() {
          return SpanTypes.MESSAGE_CONSUMER;
        }
      };

  public static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.kafka-clients-0.11");

  @Override
  protected String service() {
    return "kafka";
  }

  @Override
  protected String getComponentName() {
    return "java-kafka";
  }

  public void onConsume(final Span span, final ConsumerRecord record) {
    if (record != null) {
      final String topic = record.topic() == null ? "kafka" : record.topic();
      span.setAttribute(MoreTags.RESOURCE_NAME, "Consume Topic " + topic);
      span.setAttribute("partition", record.partition());
      span.setAttribute("offset", record.offset());
    }
  }

  public void onProduce(final Span span, final ProducerRecord record) {
    if (record != null) {

      final String topic = record.topic() == null ? "kafka" : record.topic();
      if (record.partition() != null) {
        span.setAttribute("kafka.partition", record.partition());
      }

      span.setAttribute(MoreTags.RESOURCE_NAME, "Produce Topic " + topic);
    }
  }
}
