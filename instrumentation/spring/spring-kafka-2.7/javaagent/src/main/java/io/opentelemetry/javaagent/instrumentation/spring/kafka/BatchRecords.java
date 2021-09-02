/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecords;

@AutoValue
public abstract class BatchRecords<K, V> {

  public static <K, V> BatchRecords<K, V> create(
      ConsumerRecords<K, V> consumerRecords, List<SpanContext> linkedReceiveSpans) {
    return new AutoValue_BatchRecords<>(consumerRecords, linkedReceiveSpans);
  }

  public abstract ConsumerRecords<K, V> records();

  public abstract List<SpanContext> linkedReceiveSpans();

  public static SpanLinksExtractor<BatchRecords<?, ?>> spanLinksExtractor() {
    return (spanLinks, parentContext, batchRecords) -> {
      batchRecords.linkedReceiveSpans().forEach(spanLinks::addLink);
    };
  }

  BatchRecords() {}
}
