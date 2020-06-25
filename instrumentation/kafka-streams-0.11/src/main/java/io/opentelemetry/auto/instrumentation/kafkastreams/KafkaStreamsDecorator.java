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

package io.opentelemetry.auto.instrumentation.kafkastreams;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.ClientDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import org.apache.kafka.streams.processor.internals.StampedRecord;

public class KafkaStreamsDecorator extends ClientDecorator {
  public static final KafkaStreamsDecorator CONSUMER_DECORATE = new KafkaStreamsDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.kafka-streams-0.11");

  public String spanNameForConsume(final StampedRecord record) {
    if (record == null) {
      return null;
    }
    final String topic = record.topic();
    if (topic != null) {
      return topic;
    } else {
      return "destination";
    }
  }

  public void onConsume(final Span span, final StampedRecord record) {
    if (record != null) {
      span.setAttribute("partition", record.partition());
      span.setAttribute("offset", record.offset());
    }
  }
}
