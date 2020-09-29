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

package io.opentelemetry.instrumentation.auto.kafkastreams;

import static io.opentelemetry.instrumentation.api.decorator.BaseDecorator.extract;
import static io.opentelemetry.instrumentation.auto.kafkastreams.TextMapExtractAdapter.GETTER;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import org.apache.kafka.streams.processor.internals.StampedRecord;

public class KafkaStreamsTracer extends BaseTracer {
  public static final KafkaStreamsTracer TRACER = new KafkaStreamsTracer();

  public Span startSpan(StampedRecord record) {
    Span span = tracer
        .spanBuilder(spanNameForConsume(record)).setSpanKind(Kind.CONSUMER)
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
      span.setAttribute("partition", record.partition());
      span.setAttribute("offset", record.offset());
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.kafka-streams-0.11";
  }
}
