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

package io.opentelemetry.instrumentation.auto.kafkaclients;

import static io.opentelemetry.trace.Span.Kind.PRODUCER;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import org.apache.kafka.clients.ApiVersions;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.record.RecordBatch;

public class KafkaProducerTracer extends BaseTracer {
  public static final KafkaProducerTracer TRACER = new KafkaProducerTracer();

  public Span startProducerSpan(ProducerRecord<?, ?> record) {
    Span span = startSpan(spanNameOnProduce(record), PRODUCER);
    onProduce(span, record);
    return span;
  }

  // Do not inject headers for batch versions below 2
  // This is how similar check is being done in Kafka client itself:
  // https://github.com/apache/kafka/blob/05fcfde8f69b0349216553f711fdfc3f0259c601/clients/src/main/java/org/apache/kafka/common/record/MemoryRecordsBuilder.java#L411-L412
  // Also, do not inject headers if specified by JVM option or environment variable
  // This can help in mixed client environments where clients < 0.11 that do not support
  // headers attempt to read messages that were produced by clients > 0.11 and the magic
  // value of the broker(s) is >= 2
  public boolean shouldPropagate(ApiVersions apiVersions) {
    return apiVersions.maxUsableProduceMagic() >= RecordBatch.MAGIC_VALUE_V2
        && Config.get().isKafkaClientPropagationEnabled();
  }

  public String spanNameOnProduce(ProducerRecord<?, ?> record) {
    return record.topic() + " send";
  }

  public void onProduce(Span span, ProducerRecord<?, ?> record) {
    span.setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "kafka");
    span.setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic");
    span.setAttribute(SemanticAttributes.MESSAGING_DESTINATION, record.topic());

    Integer partition = record.partition();
    if (partition != null) {
      span.setAttribute("partition", partition);
    }
    if (record.value() == null) {
      span.setAttribute("tombstone", true);
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.kafka-clients-0.11";
  }
}
